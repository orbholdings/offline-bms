# Publishing an APK release

The public, already-signed APK is in `downloads/`. The private signing key and maintenance backup stay outside GitHub.

1. Build/test the intended version and sign locally with the existing release key.
2. Replace `downloads/Offline-BMS.apk`. Update `downloads/release.json`, `downloads/SHA256SUMS`, the verification record and `docs/releases/vVERSION.md`.
3. Match the name/code to `app/build.gradle`. Run `python3 tools/verify-distribution.py` and applicable protocol, storage and Android runtime checks.
4. Commit to `main`. The workflow checks the APK/protocol and publishes a release from that exact commit. Its separate development build never replaces the signed release APK.

The workflow can also be run manually on `main`. Existing releases are left intact; publish changed bytes under a new version. It uses GitHub's automatic repository-scoped token, not a personal access token or signing secret.

The stable asset name is `Offline-BMS.apk`, so the download button uses `/releases/latest/download/Offline-BMS.apk`. A raw-file mirror is also available.

Do not upload the private maintenance ZIP, keystores, credential files or device backups. They are not needed for public source or prebuilt APK releases.
