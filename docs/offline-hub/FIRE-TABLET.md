# Fire tablet support for Offline Hub

## Goal
Use a single adaptive Android app on phones and landscape wall-mounted tablets, including compatible Amazon Fire tablets.

## Compatibility gates
- Ask for Fire model/generation and Fire OS version before promising APK support.
- Check Android API level, BLE central/GATT support, BLE permissions, local Wi-Fi, and available memory.
- Do not require Google Play Services or Google Play Store.
- Side-load a signed APK with clear installation steps; keep existing Offline BMS signing separate.
- Older Fire OS devices may need a lower minSdk, legacy Bluetooth permissions or may be unsupported.

## Tablet experience
- Adaptive 2- or 3-column dashboard with resizeable and reorderable tiles.
- Battery, solar, heater, water and lighting tiles; show demo labels until real data is available.
- Landscape-first, large touch targets, high contrast, full-screen immersive option.
- Night brightness and burn-in avoidance, screen-off schedule, optional PIN-protected settings.
- Optional kiosk launch at boot where supported; Android restrictions may require manual configuration.
- Wake on approach requires an external sensor or device-specific supported capability; do not promise camera-based always-on wake.
- Always-on charging considerations: battery swelling, heat and charge limiting where supported.

## Device topology
- A BLE peripheral may permit only one controller connection. Prefer tablet-to-local-hub-to-phone for shared access, or explicit ownership/handoff.
- Local hub can host MQTT and scheduling without WAN; app may use Android INTERNET permission for LAN sockets.
- Heater safety: no remote bypass of overheat shutdown or fan cool-down; loss of communications must not imply unsafe restart.

## Validation
Test on physical Fire hardware, not just emulator: BLE scans, reconnect, sleep, Wi-Fi without WAN, rotation, accessibility and kiosk recovery.
