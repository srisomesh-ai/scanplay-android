# ScanPlay Android

Native Kotlin WebView wrapper for https://scanplay.in — opens the studio, handles camera permission for the AR player, file picker for photo/video uploads, deep links so scanning a scanplay.in QR opens in the app, and hands off WhatsApp/UPI/Drive links to their apps.

Package: `in.scanplay.app`

## Build
Every push to `main` builds a debug APK and a release AAB/APK via GitHub Actions (Actions tab → latest run → Artifacts).

## Release signing (Play Store)
Create a keystore once and keep it safe:
```
keytool -genkey -v -keystore scanplay.jks -alias scanplay -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 scanplay.jks   # copy the output
```
Add repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (scanplay), `KEY_PASSWORD`.
Then the workflow signs `app-release.aab` for upload to Play Console.

## Release checklist
- Bump `versionCode`/`versionName` in `app/build.gradle.kts`
- Play Console: Data safety → camera used for AR, no data sold; Privacy policy URL: https://scanplay.in/privacy.html
- App links: add `https://scanplay.in/.well-known/assetlinks.json` with the release SHA-256 so QR scans open the app directly
