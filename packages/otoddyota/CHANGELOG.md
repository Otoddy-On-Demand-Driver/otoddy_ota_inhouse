## 1.0.4

* **Zero-Config Android Integration**: Added `OtoddyInitProvider` and `OtoddyFlutterLoader` plugin components to automatically inject `--aot-shared-library-name` into Flutter's initialization without requiring any custom native code or changes to `MainActivity.kt`.
* **ProGuard / R8 Rules**: Bundled `consumer-rules.pro` to keep `FlutterInjector` reflection and loader classes intact in release builds.
* **Instant App Restart**: Added `OtoddyOTA.restartApp()` utility to programmatically restart the Flutter app after downloading an OTA patch.
* **Smart Server Fallback**: Enhanced compatibility for both `/api/v1/ota/patches/check` and `/api/v1/patches/check` server route formats.
* **Fixed Rollback Notification**: Correctly invokes `onPatchReady(0)` and removes revoked patches when a remote killswitch rollback is received.

## 1.0.3

* Enhanced download URL resolution to seamlessly handle absolute URLs, CDNs, and custom reverse-proxy base URLs.
* Standardized runtime log tags to `[OtoddyOTA]`.
* Improved connection timeout and network resiliency during patch downloads.

## 1.0.2

* Enhanced download URL resolution to seamlessly handle absolute URLs, CDNs, and custom reverse-proxy base URLs.
* Standardized runtime log tags to `[OtoddyOTA]`.
* Improved connection timeout and network resiliency during patch downloads.

## 1.0.1

* Updated official package metadata and homepage to https://otoddy.com.
* Added comprehensive OTODDY documentation, branding, and enterprise examples.
* Streamlined client initialization API with `OtoddyOTA`.

## 1.0.0

* Initial release of `otoddyota`.
* Drop-in Over-The-Air (OTA) Flutter client.
* Dynamic architecture detection (`arm64-v8a`, `x86_64`) via `Abi.current()`.
* Atomic downloads with checksum and snapshot hash validation.
* Multi-endpoint server fallback and retry support.
* Emergency remote rollback / killswitch support.
