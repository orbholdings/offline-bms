# Offline Hub — foundation proposal

Offline-first Android vanlife control centre. **Offline BMS remains a standalone app**, with shared components extracted only after regression tests.

## Existing baseline
Offline BMS v1.2.9 supports JBD/JK BLE, supported Victron Instant Readout, local history and reconnect. Preserve package ID, signing, permissions, user data and existing release pipeline.

## Modules
1. Home dashboard: responsive tiles, rearrange/resize, compact phone and landscape tablet modes.
2. Energy: battery telemetry via read-only adapters for existing JBD, JK and Victron implementations.
3. Heating: AirHeaterBLE-style BLE discovery, protocol capture and read-only status first. Never assume generic E03 codes or write commands. Only enable heater control after protocol verification and physical testing. Preserve heater native safety controls and full cool-down.
4. Lights: locally controlled BLE/Wi-Fi devices only; vendor cloud dependencies excluded.
5. Sensors: ESP32 local BLE / LAN adapters, water, temperature, humidity, doors.
6. Automations: local rules engine, clear conditions and actions, per-device safety constraints. Unattended rules require an always-on local hub, not an Android background process alone.
7. Data: on-device storage, export/import, no accounts, no analytics, no cloud.

## Proposed architecture
- Android app: Kotlin, Jetpack Compose (confirm actual existing app stack before extracting code).
- Interfaces: DeviceAdapter, DeviceCapability, DeviceState, DeviceCommand, DeviceEvent.
- Connection transports: BLE GATT, BLE advertisement, local HTTP/MQTT with no external broker.
- Storage: Room/SQLite, device settings and time-series with retention controls.
- Optional local hub: Raspberry Pi / mini-PC for persistent schedules and LAN discovery.
- UI: dark-first dashboard with accessible contrast, offline indicator, tile grid and tablet layout.

## Safety and permissions
- Explicit permission per device, command confirmation for dangerous operations.
- Never bypass thermal shutdown, fan cool-down or low-voltage protection.
- Default read-only for unsupported or unknown devices.
- No Internet permission for Android-only BLE edition; LAN edition requires Android INTERNET permission for local sockets (this permission alone does not mean cloud access).
- Test disconnected operation, BLE reconnection, migrations and backup restore.

## First milestones
- M1: inventory existing source modules and create isolated Offline Hub app shell with demo data.
- M2: reuse BMS telemetry through an adapter; no BMS write operations in Hub initially.
- M3: heater BLE scan + identify firmware/controller and capture notifications, without writes.
- M4: verified heater status/controls with safety and fault handling.
- M5: modular lighting, ESP32 sensors and optional local hub.

## Acceptance criteria for M1
- Buildable debug APK with dashboard and navigation.
- Demo mode runs without devices, network or permissions.
- Offline BMS existing APK and tests unchanged.
- No misleading live values: demo clearly labelled.
