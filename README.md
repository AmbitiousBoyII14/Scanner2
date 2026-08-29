# ScannerProject

Android scanner app + Codeberg-backed license system (admin panel: admin panel.html).

## What was fixed / added in this build

**License + admin panel now work together (same keys.json schema):**
- `adminapi.cpp` is now actually compiled (it was missing from CMakeLists.txt and Android.mk, so key creation / device registration never made it into the .so).
- `liccore.h` was broken (duplicate include guards, duplicate/conflicting helper declarations, placeholder base64) and `admin.cpp` had placeholder CT_API/CT_TOK/CT_BR arrays — all cleaned; one shared header, one definition per function.
- Import = activation. When a key is imported in the APK, the app writes the phone's device hash into the key's `devices` list in keys.json and stamps `activated_at` + `expires_at`. The admin panel sees the import instantly (device count, countdown, UNIMPORTED badge clears).
- Countdown starts at import, not at creation. Trials store `minutes`, premiums store `days`; `expires_at` is set on first import and is a fixed timestamp — it never resets on revalidation or reinstall.
- Device limit enforced: `max_devices` per key is checked natively on import; phones beyond the limit are denied with "Device limit reached".
- Keys with an `import_deadline` that were never imported are rejected after the deadline.
- Admin panel: creating trial/premium keys no longer sets expiry at creation; unimported timed keys show "STARTS ON IMPORT"; EXTEND on an unimported premium key extends the stored duration.

**Scanner fixes:**
- Port scanning respects Settings ports everywhere — BugHost probe no longer scans its own hardcoded 443/8080/8443/... list, and TCP ping uses the first configured port instead of hardcoded 443.
- Open-port results show the scheme: `https://host:443 (HTTPS)`, `http://host:80 (HTTP)` so you can tell which port answered.
- CIDR support: enter `192.168.1.0/24` as a single target or put CIDR lines in a scan file — ranges expand to host IPs (capped at the free-plan host cap).

## Build

Standard Android Studio / Gradle build (NDK, ndk-build, ABI armeabi-v7a + arm64-v8a). After signing your release APK, fill `EXPECTED_SIG` in `admin.cpp` to enable the anti-repack self-check.
