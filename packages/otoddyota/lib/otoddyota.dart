library otoddyota;

import 'dart:async';
import 'dart:convert';
import 'dart:ffi';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:restart_app/restart_app.dart';

/// Callback when a patch is downloaded and ready to apply on next launch.
typedef PatchDownloadedCallback = void Function(int patchNumber);

/// Status of the code push check.
enum CodePushStatus {
  idle,
  checking,
  downloading,
  readyToApply,
  upToDate,
  error,
}

/// The main entry point for OtoddyOTA in your Flutter app.
class OtoddyOTA {
  OtoddyOTA._();

  static List<String> _baseUrls = [];
  static String? _appId;
  static String? _releaseVersion;
  static String _channel = 'stable';
  static PatchDownloadedCallback? _onPatchReady;
  static bool _initialized = false;

  /// Initialize and optionally trigger a startup check.
  ///
  /// Example:
  /// ```dart
  /// void main() {
  ///   WidgetsFlutterBinding.ensureInitialized();
  ///   InhouseCodePush.init(
  ///     serverUrls: [
  ///       'https://codepush.yourdomain.com',
  ///       'http://10.0.2.2:8080',
  ///       'http://localhost:8080',
  ///     ],
  ///     appId: 'com.example.myapp',
  ///     releaseVersion: '1.0.0',
  ///   );
  ///   runApp(const MyApp());
  /// }
  /// ```
  static void init({
    required List<String> serverUrls,
    required String appId,
    required String releaseVersion,
    String channel = 'stable',
    bool autoCheck = true,
    PatchDownloadedCallback? onPatchReady,
  }) {
    _baseUrls = List.unmodifiable(serverUrls);
    _appId = appId;
    _releaseVersion = releaseVersion;
    _channel = channel;
    _onPatchReady = onPatchReady;
    _initialized = true;

    if (autoCheck) {
      unawaited(checkForUpdate());
    }
  }

  /// Manually check and download updates.
  static Future<bool> checkForUpdate() async {
    if (!_initialized) {
      debugPrint('[OtoddyOTA] Error: init() must be called before checkForUpdate()');
      return false;
    }
    return _runCheck();
  }

  /// Gets the currently installed patch number (or null if on baseline).
  static Future<int?> getCurrentPatchNumber() async {
    try {
      final supportDir = await getApplicationSupportDirectory();
      final numberFile = File('${supportDir.path}/inhouse_patches/applied_number');
      if (numberFile.existsSync()) {
        return int.tryParse((await numberFile.readAsString()).trim());
      }
    } catch (_) {}
    return null;
  }

  /// Restarts the Android application so a downloaded patch is loaded.
  static Future<void> restartApp() async {
    await Restart.restartApp(
      forceKill: true,
      notificationTitle: 'Update successful',
      notificationBody: 'The app has been updated. Restarting...',
    );
  }

  static String get _arch {
    switch (Abi.current()) {
      case Abi.androidArm64:
        return 'arm64';
      case Abi.androidX64:
        return 'x64';
      case Abi.androidArm:
        return 'arm';
      default:
        return 'arm64';
    }
  }

  static Future<bool> _runCheck() async {
    try {
      final supportDir = await getApplicationSupportDirectory();
      final patchDir = Directory('${supportDir.path}/inhouse_patches');
      final numberFile = File('${patchDir.path}/applied_number');
      int? current;
      if (numberFile.existsSync()) {
        current = int.tryParse((await numberFile.readAsString()).trim());
      }

      for (final base in _baseUrls) {
        try {
          final success = await _tryBase(base, current, patchDir, numberFile);
          if (success) return true;
        } catch (e) {
          debugPrint('[OtoddyOTA] $base unreachable: $e');
        }
      }
    } catch (e) {
      debugPrint('[OtoddyOTA] Check failed: $e');
    }
    return false;
  }

  static Future<bool> _tryBase(
    String base,
    int? current,
    Directory patchDir,
    File numberFile,
  ) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 4);
    try {
      final payload = jsonEncode({
        'app_id': _appId,
        'release_version': _releaseVersion,
        'platform': 'android',
        'arch': _arch,
        'channel': _channel,
        'client_id': 'device',
        'current_patch_number': current,
      });

      Uri checkUri;
      if (base.endsWith('/patches/check')) {
        checkUri = Uri.parse(base);
      } else if (base.endsWith('/ota')) {
        checkUri = Uri.parse('$base/patches/check');
      } else {
        checkUri = Uri.parse('$base/api/v1/ota/patches/check');
      }

      var checkReq = await client.postUrl(checkUri);
      checkReq.headers.contentType = ContentType.json;
      checkReq.headers.set('ngrok-skip-browser-warning', '1');
      checkReq.add(utf8.encode(payload));

      var checkResp = await checkReq.close();
      if (checkResp.statusCode == HttpStatus.notFound && !checkUri.path.endsWith('/api/v1/patches/check')) {
        final fallbackUri = Uri.parse('$base/api/v1/patches/check');
        checkReq = await client.postUrl(fallbackUri);
        checkReq.headers.contentType = ContentType.json;
        checkReq.headers.set('ngrok-skip-browser-warning', '1');
        checkReq.add(utf8.encode(payload));
        checkResp = await checkReq.close();
      }

      if (checkResp.statusCode != HttpStatus.ok) return false;

      final body = jsonDecode(await checkResp.transform(utf8.decoder).join())
          as Map<String, dynamic>;

      // Handle emergency rollback if the current patch was revoked
      final rolledBackList = (body['rolled_back_patch_numbers'] as List?)
          ?.map((e) => (e as num).toInt())
          .toSet();
      if (current != null && rolledBackList != null && rolledBackList.contains(current)) {
        debugPrint('[OtoddyOTA] Patch #$current was revoked remotely! Reverting to baseline...');
        final currentPatchFile = File('${patchDir.path}/libapp.so');
        if (currentPatchFile.existsSync()) currentPatchFile.deleteSync();
        if (numberFile.existsSync()) numberFile.deleteSync();
        _onPatchReady?.call(0);
        return true;
      }

      if (body['patch_available'] != true) {
        debugPrint('[OtoddyOTA] Up to date via $base (current=$current)');
        return false;
      }

      final patch = body['patch'] as Map<String, dynamic>;
      final number = (patch['number'] as num).toInt();
      if (current != null && number <= current) {
        debugPrint('[OtoddyOTA] Already on patch #$current (latest is #$number)');
        return false;
      }

      final rawDownloadUrl = patch['download_url'] as String;
      final url = (rawDownloadUrl.startsWith('http://') || rawDownloadUrl.startsWith('https://'))
          ? rawDownloadUrl
          : '$base${Uri.parse(rawDownloadUrl).path}';
      debugPrint('[OtoddyOTA] Downloading patch #$number from $url');

      await patchDir.create(recursive: true);
      final tmp = File('${patchDir.path}/libapp.so.tmp');
      final dlReq = await client.getUrl(Uri.parse(url));
      dlReq.headers.set('ngrok-skip-browser-warning', '1');
      final dlResp = await dlReq.close();
      if (dlResp.statusCode != HttpStatus.ok) {
        debugPrint('[OtoddyOTA] Download failed with status ${dlResp.statusCode}');
        return false;
      }

      final sink = tmp.openWrite();
      await dlResp.pipe(sink);
      await tmp.rename('${patchDir.path}/libapp.so');
      await numberFile.writeAsString('$number');

      debugPrint('[OtoddyOTA] Installed patch #$number via $base -> will apply on next launch');
      _onPatchReady?.call(number);
      return true;
    } finally {
      client.close();
    }
  }
}

/// Backwards compatibility alias for InhouseCodePush
typedef InhouseCodePush = OtoddyOTA;
