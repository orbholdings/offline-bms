# Offline BMS 1.2.8

A native Android dashboard for JBD / Xiaoxiang and common JK02 Bluetooth battery monitors, with a separate Victron Instant Readout equipment view. Android 12 and newer; no runtime third-party libraries, internet permission, account, advertising or analytics.

## Install this update

Install `Offline-BMS-1.2.8.apk` over the current installation. Keep the existing app installed: the package ID (`com.simon.offlinebms`) and release signing certificate are unchanged. Saved history, preferences and encrypted passwords are retained. Existing version-1 history databases are upgraded to identify batteries by their Bluetooth address rather than their changeable display name.

Open **Batteries**, connect a saved battery or scan, enter its JBD password if needed, and connect. **Auto** detects JBD and supported JK02 layouts. You can choose a JK variant explicitly on Connect or in Edit profile.

### Quick battery switching and balancing in 1.2.8

Use the arrows at the top of Overview, Cells or History to connect the previous/next saved battery. The list wraps in name order and excludes Victron equipment. Tap the battery name to pick directly. The current tab is kept; the previous battery's readings are cleared before the switch, and any cached reading for the chosen battery is labelled Saved until live data arrives. One battery connection is active at a time. Saved encrypted passwords are reused; enable **Remember encrypted password for quick switching** on Connect when a JBD battery requires one. The protocol chosen in each battery's profile is retained. Victron broadcast monitoring continues when switching BMS batteries.

The **Balance** button appears in Overview's Battery status section and on Cells. It enables/disables automatic balancing with the existing BMS voltage and cell-difference limits. Enabled and actively balancing are displayed separately. Demo and saved readings cannot send balancing commands.

For supported JK02 24S/32S layouts, the button reads the current setting, shows a review, sends only the balancing-enable command, then reads it back. It does not change balance thresholds, charge/discharge switches or other settings. A missing/mismatched readback is shown as unconfirmed; the app never retries a control write automatically. A settings response must match the current telemetry's cell count, capacity and known layout. JK04 and other unknown layouts cannot use this control.

For classic JBD, the button uses the existing parameter read, review, original-value backup, bit-preserving write, commit and verification flow. A settings password may be required. As shown in the existing review, JBD's commit also resets protection counters; their pre-write values are kept in the local snapshot. Unsupported/extended layouts remain blocked. The JBD force-balance test command is not used.

Battery switching waits while a settings/control operation is in progress. The monitoring service also rejects competing connection requests, and readings/callbacks from the previous connection cannot populate the new battery. No new physical balancing test is claimed; verification here uses wire vectors and Android runtime simulations.

### Visual refresh and centred navigation in 1.2.7

Bottom tabs now centre each text label beneath its icon, use equal-width touch areas, correctly centred icon artwork and a clear selected state. Text labels use one bounded line rather than a horizontally scrolling layout. The tab for Victron equipment stays Batteries; parameters stay under Tools. Native pressed feedback and accessible selected/button states are retained.

Dark and light themes have clearer card outlines, more consistent corner sizes, restrained secondary buttons and tidier typography. The dashboard has a slimmer charge gauge and separates remaining charge from reported capacity. Saved batteries appear before the Victron link; unknown chemistry labels are omitted.

A connected battery shows **CONNECTED** and **Open dashboard**, which opens its current readings without reconnecting. During an attempt its action reads Connecting and is disabled; failed/stopped connections return to Connect. Status changes update the cards in place. Saved battery names, profiles, keys, history and supported protocols are retained. The user confirmed the previous 1.2.6 JK fix works on the physical battery.

Native UI checks cover dark/light at 360/393/412 dp, 1.3x text, every tab's selected state, centred text/icon coordinates, equal hit areas, connected-card transitions and opening the existing Bluetooth connection. Rendered battery and dashboard screens were also inspected.

### Captured JK firmware 11.55 fix in 1.2.6

The supplied JK_B2A8S20P firmware 11.55 status captures use **JK02_32S** even though the battery has four cells. The saved profile was JK02_24S, which reads the pack voltage from the wrong offset. Select **JK02_32S** on Connect (Auto also accepts these captures). The profile and connection forms now explain that 24S/32S name data formats rather than the actual cell count. A wrong explicit format is kept strict but gets a correction only when the alternative layout passes every check; the app does not silently rewrite the saved profile.

The captures also have the exact signed raw temperature value 0xF830 (-200 C) in MOS/T1/T2 while those low absent-mask bits are clear. The parser now omits that exact unavailable marker, rather than rejecting the whole measurement. Other out-of-range temperatures, corrupt frames, invalid SOC/current/capacity/MOSFET states and incoherent pack/cell voltages remain rejected. Missing temperatures display **Temperature readings unavailable** and are never changed to zero.

Both actual checksum-valid 300-byte captures are included as measurement-only regression fixtures, without names, addresses, identity or settings packets. They decode to four cells (3.285/3.281/3.285/3.284 V), 13.134 V pack voltage, 89% SOC, 280 Ah nominal capacity, 248.965/248.964 Ah remaining, 0 A and both MOSFETs on. Native Android tests replay the original 128/22/128/22-byte fragmentation through the client/service/dashboard in Auto and JK02_32S. They also verify the wrong-profile correction and that a later stream timeout does not falsely report a profile mismatch. This proves the reported decoder failure is addressed on captured data; a fresh physical connection still needs user confirmation.

### JK diagnostics and sensor handling in 1.2.5

Connection diagnostics now retain one bounded private snapshot across a stopped service or reopened app. An idle service no longer replaces the failed attempt with an empty report. The snapshot includes the chosen protocol, model/firmware, packet and frame counts, detailed reasons for each rejected JK02 layout, and at most two checksum-valid rejected type-0x02 measurement frames. Raw identity and settings frames, passwords and readout keys are excluded. The snapshot is capped at 65,536 characters and is not included in history backups.

Use **Overview → This connection → Export connection diagnostics**, or **Tools → App details & diagnostics → Export diagnostics**. Export captures the latest report before opening Android's picker. A retained report is clearly labelled as a saved attempt, with its original timestamp; this does not imply a live connection.

The parser now honours the documented absent-temperature-sensor bits for both JK02 layouts. Explicitly missing sensors are omitted, including sentinel values, while present sensors still require valid temperatures. Labels such as MOS/T1/T2 follow the actual reported sensor and survive JSON history/backup round trips. Voltage/current/capacity/SOC and cell-layout validation remain enforced. This fixes confirmed parser and diagnostic defects; the exact cause on the user's JK device is not yet established because the supplied diagnostic file contained no connection attempt. JK04 remains unsupported.

### Overview and wider compatibility in 1.2.4

Victron devices now appear below the battery section on Overview. Each card shows its name, supported measurements, timestamp and LIVE / STALE / SAVED status. The Start/Stop button controls Victron receiving independently, so stopping it does not disconnect a JBD/JK battery. Use Manage Victron devices for addresses, keys, profiles and exports.

The receiver now implements 13 stable Instant Readout record formats:

| Record | Device family |
| --- | --- |
| 0x01 | Solar charger |
| 0x02 | Battery monitor / Smart Battery Sense |
| 0x03 | Inverter |
| 0x04 | DC/DC converter |
| 0x05 | SmartLithium |
| 0x06 | Inverter RS |
| 0x08 | AC charger |
| 0x09 | Smart BatteryProtect |
| 0x0A | Lynx Smart BMS |
| 0x0B | Multi RS |
| 0x0C | VE.Bus |
| 0x0D | DC energy meter |
| 0x0F | Orion XS |

The product and firmware must broadcast one of these formats. GX BLE record 0x07 remains provisional and unsupported; GX/Cerbo LAN, VRM, stored trends, configuration changes and future/unassigned records are not implemented. This is not a claim that every Victron product works. SmartLithium cell values below/above the encoded range are shown as bounds rather than invented exact voltages. Additional text details survive history, JSON/CSV exports and backups. Test-only beacons are ignored, and an unsupported record no longer permanently suppresses later supported packets from that device.

JK connection handling now discovers a dedicated FFE2 transmit characteristic when present, avoids duplicate notification subscriptions and tries an alternate read-only channel/write mode if there are no incoming packets. Diagnostics distinguish missing packets from invalid frames/layouts. A fragmented simulated 4-cell, 280 Ah, 90% reading reaches the dashboard in both API 31 and 35 tests. Physical JK confirmation remains necessary. Use Auto or the correct JK02 variant, keep the phone close and close the manufacturer's app. The screenshot's -102 dBm is a very weak signal. JK04 is still not decoded.

### Victron AC charger update in 1.2.3

Adds Instant Readout AC Charger records (`0x08`): reported voltage/current and derived DC power for up to three outputs, temperature, AC input current, charge state and charger error. Unavailable measurements are omitted. The decoder follows Victron's published unsigned 13-/11-bit output fields and accepts future trailing data; incomplete records are rejected.

Pasted Bluetooth addresses now accept compact, colon-separated or hyphen-separated forms. They are normalized before saving so existing profiles and encrypted keys keep using the same address. The setup dialog identifies an invalid address or readout key separately and scrolls when the keyboard is open.

After updating, open **Batteries → Victron equipment** and start monitoring. Existing device profiles and keys are retained. The JBD startup correction from 1.2.2 remains in place. AC charger measurements need comparison with VictronConnect on the physical device; the tests use synthetic reference data.

### Connection startup correction in 1.2.2

The Bluetooth client previously reported “Connecting” before `connectGatt` returned a handle. The monitoring service interpreted the missing handle as a disconnection, stopped monitoring, and ignored subsequent complete readings. Bluetooth could still return valid data and publish a “Live” status while the dashboard remained empty.

The client now marks the connection attempt active before publishing that callback, clears the attempt on disconnect/failure, and handles a null Android GATT handle as a connection failure. The service therefore remains active through startup and accepts the full reading. Diagnostics also show whether monitoring and the Bluetooth attempt are active.

The regression is reproduced against the previous code on Android API 31 and 35: both startup checks fail on each API, while the failure/stop controls pass. With this fix, all eight connection lifecycle cases pass, together with the existing 24 launch/parameter/archive cases. There were 283 passing checks in that release (214 protocol/authentication/feature/parameter, 37 storage and 32 Android runtime cases). No physical battery connection result is claimed for 1.2.2 yet.

If a connection still fails after installing this update, open **Tools → App details & diagnostics → Export diagnostics** immediately after the attempt and return the text file. Passwords and encoded login packets are excluded.

### Startup correction in 1.1.1

Version 1.1.0 could close immediately because it requested the window's inset controller before Android had created its decor view. Version 1.1.1 creates the screen first and applies the system-bar appearance after attachment. The crash was reproduced and a regression suite now covers fresh launches, retained battery preferences, both themes, system theme, theme recreation, all screens, demo rendering and idle monitoring-service creation on Android API 31 and 35 using Robolectric with native resources/graphics. These are host Android runtime tests, separate from physical-phone and Bluetooth testing.

## What is included

- A redesigned dashboard with a large charge gauge, signed power flow, voltage/current cards, cell spread, temperature, balancing and protection status.
- Dark, light and system themes, Celsius/Fahrenheit display, optional keep-screen-awake mode and clearly labelled demo data.
- Named battery/device profiles, chemistry labels, capacity and nominal-voltage overrides for estimates, reserve percentage and notes. Profiles never change hardware settings.
- One live JBD/JK connection at a time, with switching between up to 100 saved battery/equipment profiles.
- Background Bluetooth monitoring with a visible notification and **Stop monitoring** action. It continues through screen locking and export pickers when Android allows it. Background monitoring can be disabled in Tools.
- Optional local recording, 5/10/30-second read intervals, 5/10/30/60-second save intervals and 7/30/90-day retention. Actual saved frequency is limited by the read frequency. Each BMS/equipment history type has a further cap of roughly 260,000 samples.
- Cell bar charts, sortable two-column cell cards, min/max/spread and reported balancing flags. JK's overall balancing indicator is shown without inventing per-cell flags.
- Seven draggable history graphs: SOC, power, voltage, current, maximum temperature, cell spread and remaining Ah. Large datasets are averaged for display. Temperature history uses Celsius, as labelled.
- Per-battery and time-range history selection; measured kWh/Ah charged and used, voltage range, peak power, connection sessions and local alert/connection events.
- Usable-energy and runtime estimates above a chosen reserve; time to full while charging; a load calculator with inverter efficiency.
- Per-battery local notifications for low SOC, high temperature, cell spread, high/low cell voltage, reported BMS protection flags, disconnections and stale readings. Alerts are off by default. Numeric thresholds require two successive readings and use recovery hysteresis; BMS flags are immediate.
- Latest-reading, filtered-history, all-BMS-history and equipment-history exports in CSV/JSON.
- Self-contained HTML battery reports with embedded SVG charts, cell tables, temperatures, status and notes. These open without external fonts, scripts or resources and can be printed.
- Streaming JSON backups of profiles, BMS readings and Victron equipment readings; merging restores that deduplicate readings and preserve existing profiles. Invalid backups roll back database changes. Passwords/readout keys, alert-event lists and session-summary tables are excluded. Restored BMS records retain session IDs for energy calculations.
- Local connection diagnostics and an offline user guide.
- A JBD hardware parameter editor, reached through **Tools → BMS parameters**, with 31 mapped fields, grouped settings, original-value snapshots and verified individual saves. Unsupported firmware values remain unavailable.
- An optional **Buy me a coffee** button in Tools, opening https://square.link/u/kAddgU4q in the external browser. The browser/payment page needs internet; battery monitoring does not.

**Positive current and power mean charging; negative values mean discharging.** SOC is reported by the BMS. Capacity overrides only affect estimates on this phone.

Energy totals use trapezoidal integration of actual measured samples, separating charging/discharging at zero crossings. Gaps longer than 90 seconds and changes of session are excluded. Totals cover the intervals recorded by this app, not unobserved overnight consumption. Dense graphs can display averaged points; statistics use the saved samples.

## Hardware compatibility

| Hardware / interface | Implemented | Notes |
| --- | --- | --- |
| JBD / Xiaoxiang BLE `FF00` | Yes | Notifications on `FF01` and supported `FF02`; measurement registers 03/04/05. Published BLE password challenge/response and legacy register-06 current-password authentication. |
| Classic JBD hardware parameters | Conditional | Requires matching live cell count/capacity scale, plausible classic voltage/current registers, a fresh read on this connection and any settings password required by the BMS. Extended telemetry scales and mismatching layouts are locked against edits. |
| JK02 24-slot BLE `FFE0/FFE1` | Yes | Common JK02_24S layout; telemetry and device-info requests only. |
| JK02 32-slot BLE `FFE0/FFE1` | Yes | Common JK02_32S layout, including signed current and its shifted fields. Auto requires a unique plausible match; manual layout selection is available. |
| Older JK04 / Bluetooth Classic | No | A different decoder/transport is needed. |
| Victron Instant Readout | 13 formats | Solar Charger, Battery Monitor / Smart Battery Sense, Inverter, DC/DC Converter, SmartLithium, Inverter RS, AC Charger, Smart BatteryProtect, Lynx Smart BMS, Multi RS, VE.Bus, DC Energy Meter and Orion XS. |
| Provisional GX BLE record / GX or Cerbo LAN / future records | No | These require a validated format or a different interface; not every product has Instant Readout. |

The user confirmed that JBD connects on version 1.2.2. This update retains that fix and adds the AC charger parser; no physical-device parameter write or new JK/Victron hardware result is claimed. Firmware variants can differ. Auto refuses ambiguous JK layouts. Unsupported Victron records produce a compatibility message instead of fabricated readings.

JK telemetry does not use the JBD password login. The app sends only JK requests 0x96 and 0x97; it does not change the JK device/setup password. Devices requiring OS bonding or a different authentication protocol may need additional support.

This release monitors supported JBD/JK/Victron devices and edits selected classic JBD EEPROM parameters. It does not switch MOSFETs, reset remaining capacity, clear active hardware warnings, calibrate voltage/current, change hardware cell/sensor configuration, change/reset passwords or update firmware. JK hardware edits are limited to the balancing-enable button above; Victron hardware settings are not edited. Manufacturer menus involving chip-specific short-circuit fields, calibration or reset operations are not replicated.

## Editing JBD battery settings

1. Connect to the JBD battery and wait for valid readings.
2. Open **Tools → BMS parameters → Read parameters from battery**. Supply the BMS settings password if needed; an encrypted password saved for this battery can prefill the field. It may differ from the Bluetooth-module password.
3. Filter the groups or browse the values read from the BMS. Saved reference copies can be viewed when disconnected but cannot be written. Unavailable fields remain disabled.
4. Choose **Edit**, enter a value, then **Review**. The review shows the battery/address and old → new value.
5. Select **Save & verify** only for values specified for your cells/BMS. Encoding ranges are broad protocol bounds, not recommended protection settings.

Editable groups are design/cycle capacity and estimated self-discharge; cell/pack voltage protection and release thresholds; charge/discharge overcurrent thresholds; eight charge/discharge temperature thresholds; balance start/difference and two balancing function flags; and six SOC voltage points. Temperatures always use Celsius. Discharge current protection is entered as a positive magnitude and encoded as signed negative current. Unknown function bits are preserved when a balancing flag changes.

The editor uses the published common classic-JBD register layout. It compares cell count and design-capacity scale with live telemetry and validates voltage/current plausibility before enabling edits. This is a compatibility check, not proof that every firmware variant implements the map identically. Some boards require a separate settings password or reject EEPROM access. Extended current/capacity encodings are not edited. Do not force another board's layout.

Only one register is written per save. The app suspends its telemetry requests, enters parameter mode, reads the parameters again, rejects a changed/expired review, and synchronously saves a local original snapshot before sending the write. It checks the acknowledgement and immediate readback, commits, re-enters parameter mode to read back the persistent value, and closes parameter mode. Commands are spaced and responses are checked; no timed-out write is automatically retried. If interrupted or verification fails, the app reports that the battery may have changed and requires a new read. It does not claim an automatic rollback.

**The classic JBD commit command (`0x01`, data `0x2828`) also resets the BMS protection counters.** The values read before writing are included in the local snapshot. Reading uses the normal non-committing exit (`0x0000`) and does not send that reset/commit command. Active hardware alarms and the app's recorded events are separate from these BMS counters.

**Export parameter snapshots** creates a JSON reference containing the latest read and values before the last write, without credentials. These snapshots are separate from the profile/history backup. **Use value from before last change** fills an individual edit with the earlier value; it still requires review and a verified new save. There is no bulk hardware restore. A later save replaces the pre-write snapshot, so export it separately when needed.

Basic information, BMS protection counts and the measurement-shunt resistance are displayed as references. Calibration, password changes, factory resets, protection delay/short-circuit chip settings and hardware sensor/cell configuration are outside this editor. JK and Victron retain their existing monitoring functions and local profiles.

## Victron setup

1. Connect locally to the device in VictronConnect.
2. Open **Settings → Product info** and enable **Instant readout via Bluetooth**.
3. Select **Show** beside **Instant readout details**; copy its MAC address and 32-character encryption key.
4. In Offline BMS choose **Batteries → Victron equipment → Add Victron by address & key**. The address can be pasted with or without colons; hyphen-separated addresses are also accepted.
5. Start Victron monitoring and stay within Bluetooth range. A general scan can also identify nearby Victron advertisements and open their setup form.

The key is device-specific and differs from the Bluetooth PIN. It is stored using the existing Android Keystore AES-GCM credential store under a separate namespace, excluded from backups/diagnostics, and can be removed through **Forget passwords & Victron readout keys**. Re-enter keys after moving a backup to a different phone.

Victron reads manufacturer advertisements using the owner-supplied key, not an active GATT connection. Multiple saved Victron devices can be received while one JBD/JK battery is monitored. The app does not download Victron stored trends or use VRM. Supported records must actually be broadcast by the particular product/dongle firmware. If broadcasts stop during a VictronConnect connection, close that connection after setup.

## Privacy and permissions

| Permission | Purpose |
| --- | --- |
| BLUETOOTH_SCAN (`neverForLocation`) | Discover batteries and receive Victron advertisements. |
| BLUETOOTH_CONNECT | Connect to the selected BMS. |
| POST_NOTIFICATIONS | Local battery alerts and the monitoring notification. |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_CONNECTED_DEVICE | Keep user-started Bluetooth monitoring running outside the Activity. |
| WAKE_LOCK | A renewable, bounded partial wake lock during active monitoring; released on stop, disconnection and service destruction. |

There is **no INTERNET, location or broad storage permission**. Android system backup/transfer is disabled. Optional passwords and Victron keys use a non-exportable Android Keystore AES-GCM key, random IVs and address-bound authenticated data. Credentials and encoded login packets are never logged.

Monitoring consumes phone battery; stop it from the notification or Batteries/Equipment when finished. It does not restart automatically after a reboot or process death. Android power management and Bluetooth range can still interrupt it. Alerts require an active connection/receiver and Android notification permission. They supplement the BMS's own protections.

To keep exported files offline, choose local phone storage in Android's file picker. Selecting a cloud provider can upload that file using the provider's own app.

## Validation

- 46 JBD telemetry, 37 authentication, 66 offline feature/JK/Victron and 65 JBD parameter/transaction checks.
- 56 AC charger/address checks and 91 wider Victron format checks, including independent encrypted vectors, missing and clipped cells, signed fields, all short lengths and trailing extensions.
- 40 JK absent-sensor and diagnostic-reason checks, including all missing sensors, signed readings and malformed data.
- 88 captured JK32 replay and validation checks, using both real status captures plus malformed-data controls.
- 40 real SQLite storage/backup/export checks, including backward-compatible text details and CSV preservation.
- 30 balancing wire-vector, settings-layout and function-bit preservation checks.
- 70 Android runtime cases on API 31/35 with Robolectric 4.14.1: existing launch, parameter and lifecycle regressions, Overview card placement/freshness in dark/light themes, preserving BMS monitoring while stopping Victron, JK combined/separate transmit discovery, fallback, and fragmented four-cell data reaching the dashboard. Both API 31 and 35 notification callback forms are exercised. Additional cases exercise failed telemetry capture without identity/settings credentials, retention after service recreation, bounded storage, export through Overview/Tools without an active service, and correct sensor labels after a JSON round trip. Archive metadata extraction is separate from cryptographic APK verification.
- Total: **629 automated checks and Android runtime cases**. BLE and new hardware readings are simulated; no new physical JK/Victron result or parameter write is claimed.
- Official SDK 35 compilation, D8, final archive/DEX/resource alignment checks and embedded signatures; certificate continuity with the supplied 1.2.6 APK. The six existing permissions are unchanged and there is no INTERNET permission.

## Build and reproduce tests

Use JDK 17 and Android SDK 35/build tools 35. The installed application has no third-party runtime dependency. Development/build tools and host test dependencies may need downloading initially.

```sh
./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest
sh tests/run.sh
python3 tests/run-storage-host.py
```

The first command produces an unsigned APK. `tools/build-sdk.py` offers a dependency-free signed SDK build using `--android-jar`, `--build-tools`, `--work` and `--output`. Restore the separately supplied maintenance signing directory to `private-signing/` first. `tools/package-release.py` verifies certificate continuity, version and permission scope, and creates the source/download pack.

Host test dependencies are pinned by SHA-256 in `tests/host-libs.json` and cached outside the app. No host test adapter or dependency is compiled into the APK. The existing `LocalDataTests` instrumentation runner can be used on an emulator to validate native SQLite/exports; its execution is separate from the host results reported here.

`app/src/test/java/com/simon/offlinebms/LaunchTest.java` contains the Android launch/parameter regression tests. `ConnectionLifecycleTest.java` covers the active connection-start callback, acceptance of a full reading, real failure handling and explicit stop. Gradle enables Android test resources; Robolectric and JUnit are test-only dependencies and are not included in the installed APK. The runtime-test suite needs its SDK/JNI dependencies downloaded once and JDK 17. See `tests/evidence/battery-controls-1.2.8.txt` for this update; the earlier 1.1.1 crash evidence is retained separately.

Keep `maintenance/` and `private-signing/` private. The source ZIP excludes signing credentials. An unchanged application ID and signing key are required to preserve installed data during future updates.

## Interoperability references

Original Java implementations use the published packet/layout information; no proprietary APK has been modified.

- JBD/Overkill [support and protocol resources](https://overkillsolar.com/support-downloads/).
- Maintainer's [JBD BLE interoperability implementation](https://github.com/syssi/esphome-jbd-bms/blob/main/components/jbd_bms_ble/jbd_bms_ble.cpp).
- Supplier's [JBD register/EEPROM map, units and factory-mode commands](https://gitlab.com/Overkill-Solar-LLC/overkill-solar-bms-tools/-/blob/master/JBD_REGISTER_MAP.md).
- JBD's [published communication protocol](https://cdn.shopifycdn.net/s/files/1/0606/5199/5298/files/JDB_RS485-RS232-UART-Bluetooth-Communication_Protocol.pdf?v=1682577935).
- Maintainer's [JK BLE interoperability implementation and example packets](https://github.com/syssi/esphome-jk-bms/blob/main/components/jk_bms_ble/jk_bms_ble.cpp).
- Victron's [Instant Readout manual and compatibility table](https://www.victronenergy.com/media/pg/VictronConnect_app/en/stored-trends---instant-readout.html).
- Victron's [Extra Manufacturer Data protocol](https://communityarchive.victronenergy.com/storage/attachments/extra-manufacturer-data-2022-12-14.pdf).
- Victron staff's [Orion XS layout confirmation](https://community.victronenergy.com/t/orion-xs-12v-12v-50a-bluetooth-advertising-data/2183).
- Victron staff's [AC charger layout clarification](https://community.victronenergy.com/t/orion-xs-12v-12v-50a-bluetooth-advertising-data/2183/12).
- Maintainer's [Victron advertisement container reference](https://github.com/keshavdv/victron-ble).
- Android's [connected-device foreground service guidance](https://developer.android.com/develop/background-work/services/fgs/service-types) and [background Bluetooth guidance](https://developer.android.com/develop/connectivity/bluetooth/ble/background).

## Standalone packaging in 1.2.1

Version 1.2.0 was reported as rejected by the phone installer. The delivered
bytes matched the generated APK and passed archive, checksum, alignment,
signature and Android framework metadata parsing checks. The phone-side cause
was not established from the screenshot; no confirmed installation fix is
claimed. Version 1.2.1 is a fresh clean rebuild with explicit embedded v1/v2/v3
signatures, no v4 incremental-install companion, and final-output validation.

If a direct APK download is rejected, download the installation ZIP, extract
it, and open Offline-BMS-1.2.1.apk through Samsung My Files > Downloads.
Keep the existing app installed. The installation ZIP contains no signing
credentials; the complete source/download pack retains the private maintenance
backup.

Four additional API 31/35 package tests parse the standalone release and
known working APK and extract certificate metadata. Framework cryptographic
digest verification is skipped because Android's mmap verifier depends on an
Android-specific DirectByteBuffer constructor unavailable on the host JDK.
SDK apksigner verifies the actual cryptographic signatures separately at
API 23, 24, 28, 31, 35 and 36. These tests do not install onto a phone.

`tools/verify-apk.py` checks the final signed APK: required/unique entries,
ZIP CRCs, uncompressed/aligned resource table, DEX lengths/SHA-1/Adler-32,
standalone output and signature verification across those SDK levels.
