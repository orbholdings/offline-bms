# Victron AC charger fixture

Synthetic data, not captured from the user. No real device keys are included.
Reference: Victron Extra Manufacturer Data, AC Charger table (page 6), plus
Victron staff confirmation that the published layout is finalized:
https://communityarchive.victronenergy.com/storage/attachments/extra-manufacturer-data-2022-12-14.pdf
https://community.victronenergy.com/t/orion-xs-12v-12v-50a-bluetooth-advertising-data/2183/12

`victron-ac-charger.hex` uses public dummy AES key
`000102030405060708090a0b0c0d0e0f`, counter 0x5678 and a synthetic model 0xA343.
AES-CTR IV: 78 56 followed by fourteen zero bytes.
Independent plaintext: `0300a0852528a505ffffffc10d`.
Expected: Bulk, no charger error; output 1 at 14.40 V / 30.0 A;
output 2 at 13.20 V / 4.5 A; output 3 unavailable; 25 C and 2.7 A AC input.
The encrypted fixture was produced independently with Python cryptography.
Tests use the literal vector, not the production parser to generate packets.

## Additional 1.2.4 vectors

`victron-type-05.hex`, `victron-type-09.hex`, `victron-type-0f.hex` are independent synthetic AES-CTR fixtures using the same public dummy key. They encode SmartLithium, Smart BatteryProtect and Orion XS respectively. Plaintexts and expected values are fixed in VictronFormatsTests.java. BatteryProtect byte offsets were checked against the maintained implementation because the original PDF table has inconsistent header offsets. No real user credentials are included.

## Captured JK firmware 11.55 measurements in 1.2.6

`jk32-fw11-55-first.hex` and `jk32-fw11-55-last.hex` are the two original checksum-valid type-0x02 status frames supplied with the JK_B2A8S20P firmware 11.55 diagnostic report. Only measurement bytes are retained; no user names, Bluetooth addresses, identity/settings frames or credentials are included. They retain the 0xF830 temperature marker and absent mask 0xF0 that caused the failure. The expected decoded values are independently specified in JkCapturedTests.java; the parser is not used to generate the fixtures.
