# Offline Hub Android prototype

A separate Android application in the existing Offline BMS repository. This is **v0.1 demo**, not yet a release APK.

- Sideloadable standard Android APK (no Google Play Services dependency)
- Min Android API 26 (Android 8), subject to physical Fire tablet testing
- Dark adaptive phone / landscape tablet dashboard
- Explicit DEMO readings; no misleading real-device values
- No Internet permission, telemetry, accounts or cloud
- No Bluetooth permissions or device commands yet

## Build
Open the `offline-hub` folder in Android Studio with Android SDK 35 and JDK 17 or later. Run `gradle :app:assembleDebug` (or use Android Studio Build APK). Output: `app/build/outputs/apk/debug/app-debug.apk`.

This prototype deliberately does not include a Gradle wrapper binary; Android Studio can configure Gradle. The build has not yet been verified on physical hardware.

## Next
Connect read-only BMS telemetry through a reusable adapter, then add heater BLE discovery and protocol identification. Never enable heater commands without verified protocol and safety tests.
