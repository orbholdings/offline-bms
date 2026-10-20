# Offline BMS

**Your battery. Your data.** Android monitoring for JBD and JK batteries, plus supported Victron Instant Readout broadcasts.

No accounts, ads, analytics or cloud backend. The app has **no Internet permission**. Profiles, encrypted optional credentials and recorded history stay on your phone unless you export them.

## Download

### [Download Offline BMS APK](https://github.com/orbholdings/offline-bms/releases/latest/download/Offline-BMS.apk)

[Releases](https://github.com/orbholdings/offline-bms/releases/latest) · [Direct APK mirror](https://raw.githubusercontent.com/orbholdings/offline-bms/main/downloads/Offline-BMS.apk) · [Checksums](downloads/SHA256SUMS)

**Version 1.2.8 · Android 12 or later.** Open the downloaded APK on your phone to install. If Android asks, allow installation from your browser/file manager. Install updates over the existing app to keep history, profiles and saved keys. The release retains the original package ID and signing certificate.

## Features

- Live charge, voltage, current, power, temperatures, alarms and cell voltages.
- Cell charts, min/max/spread, balancing status and protection flags.
- Top arrows to switch batteries from Overview, Cells or History; tap the name for a picker. One JBD/JK battery is live at a time.
- A reviewed Balance button on Overview and Cells for supported JK02 and classic JBD devices, with readback confirmation and existing balancing limits.
- Local history, charts, measured energy, approximate runtime, profiles and alerts.
- CSV/JSON exports, reports and backup/restore through Android's file picker.
- Supported classic JBD parameter edits with original-value snapshots and verification.
- Dark/light themes, centred tabs and a demo that never writes to a battery.

## Compatibility

| Equipment | Support |
| --- | --- |
| JBD / Xiaoxiang BLE | Telemetry, supported password authentication and verified classic parameter/balancing settings. Extended/unknown settings layouts stay blocked. |
| JK BLE | JK02 24-slot/32-slot telemetry, Auto detection and balancing enable/disable. These names describe the data format, not the cell count. JK04 is unsupported. |
| Victron | Supported encrypted Instant Readout broadcast formats, using each device's Bluetooth address and key. |

Victron formats: Solar Charger, Battery Monitor / Smart Battery Sense, Inverter, DC/DC Converter, SmartLithium, Inverter RS, AC Charger, Smart BatteryProtect, Lynx Smart BMS, Multi RS, VE.Bus, DC Energy Meter and Orion XS. A product must broadcast a supported format; not every Victron model is covered. GX/Cerbo LAN and VRM are not included.

Live JBD and JK operation was confirmed during development. **The new balancing controls have automated coverage and still need physical-device verification.** Enabled and actively balancing are different states. JBD settings commits reset protection counters; the app explains this during review and keeps a pre-write snapshot.

## Getting started

1. Allow Nearby devices, enable Bluetooth and close other apps connected to the BMS.
2. Open Batteries, scan and connect. Choose Auto or the supported protocol and enter a password if required.
3. Give each battery a useful name. Remember its encrypted password on Connect for quick switching.
4. Switch with the arrows or name picker. Saved readings remain labelled until live data arrives.
5. For Victron, enable Instant Readout in VictronConnect and add its address/key under Victron equipment.

[Full user guide](docs/USER-GUIDE.md) · [Release notes](docs/releases/v1.2.8.md)

## Build and checks

Open in Android Studio, or use JDK 17 and Android SDK 35:

```sh
./gradlew :app:assembleDebug
sh tests/run.sh
./gradlew :app:testDebugUnitTest
```

Debug APKs use a development certificate. They are intended for development and cannot update the signed release in place. The project uses Gradle 8.13, Android Gradle Plugin 8.9.2 and test-only Robolectric 4.14.1.

Version 1.2.8 passed **629 automated checks/cases**: 519 protocol/control, 40 storage and 70 Android runtime cases on API 31/35. Dark/light previews, three phone widths and larger text were checked. [Validation](tests/evidence/battery-controls-1.2.8.txt) · [APK verification](downloads/VERIFICATION.txt)

GitHub Actions checks the protocol and published APK, builds a development APK, and publishes the existing signed APK as a release. The private signing key is not required or uploaded. [Release maintenance](docs/RELEASING.md)

## Privacy and support

Optional credentials use Android Keystore encryption and are excluded from backups/diagnostics. Android app backup is disabled. Exports contain the data you choose; select local storage to keep exports offline. Background monitoring uses a visible notification and the connected-device service.

[Buy Simon a coffee](https://square.link/u/kAddgU4q) · [MIT licence](LICENSE)
