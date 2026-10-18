# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-module native Android app (Kotlin, Jetpack Compose) that listens for incoming SMS from configured phone numbers and forwards them to an email address via the Resend REST API. Package: `com.example.smsforwarder`, minSdk 24, target/compileSdk 34.

Gradle 8.3 must run on **JDK 17** (CI uses 17). JDK 21+ (including Android Studio's bundled JBR 25) fails with "Unsupported class file major version". Set `JAVA_HOME` to a JDK 17 first (one may be at `~/.gradle/jdks/`). `local.properties` (with `sdk.dir`) is not tracked.

## Commands

```bash
./gradlew assembleDebug                 # build debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # JVM unit tests (what CI runs)
./gradlew ktlintCheck                   # lint (CI fails on violations)
./gradlew ktlintFormat                  # auto-fix formatting
./gradlew connectedDebugAndroidTest     # instrumented/Compose UI tests (needs device/emulator)
adb emu sms send 5550123456 "hello"     # simulate an incoming SMS on an emulator

# single unit test
./gradlew testDebugUnitTest --tests "com.example.smsforwarder.SmsProcessorTest"
./gradlew testDebugUnitTest --tests "com.example.smsforwarder.SmsProcessorTest.someMethod"
```

## Architecture

SMS → `SmsReceiver` → `ForwardWorker` → `EmailSender`, with the decision logic kept in `SmsProcessor` so it is testable on the JVM:

- `SmsReceiver` — manifest-registered `BroadcastReceiver` for `SMS_RECEIVED`. It joins multipart PDUs per sender, uses `goAsync()` to read the target list, and enqueues a `ForwardWorker` only for matching messages. It does no network work itself.
- `ForwardWorker` — `CoroutineWorker` with a network constraint and exponential backoff. It maps `SendResult.Retryable` (IO errors, 429, 5xx) to `Result.retry()` up to 10 attempts, and everything else to failure. When `encrypt_pdf` is on, it attaches an `EncryptedSmsPdf` instead of putting the SMS in the email (generic subject, no sender or body in clear text), and it fails rather than falling back to plain text if the PIN is missing. It writes a human-readable outcome to the `last_status` setting, which the UI shows. The "Send Test Email" button reuses it with `isTest = true`.
- `email/` — the email provider layer. `EmailSender` is the interface (`Email` in, `SendResult` out). `ResendEmailSender` owns the Resend wire format, the `Idempotency-Key` header (the worker passes its work id, so retries can't send duplicates) and the HTTP-status → `SendResult` mapping. `createEmailSender()` is the only place that picks the provider. Provider-specific code should not leak out of this package. `ResendEmailSenderTest` runs against OkHttp `MockWebServer`.
- `SmsProcessor` — pure Kotlin with no Android dependencies: sender matching (separators like spaces, dashes and parentheses removed; case-insensitive suffix match; targets shorter than `MIN_TARGET_LENGTH` = 4 are ignored; the `forward_all` setting bypasses the list entirely) and multipart joining. Put new business logic here so it can be covered by `app/src/test` unit tests. Those tests use the real `org.json` instead of the android.jar stub.
- `EncryptedSmsPdf` — draws the SMS with Android's `PdfDocument`/`StaticLayout`, so every script and emoji the phone supports works. Then PdfBox-Android (`com.tom-roush:pdfbox-android`) applies AES-256 password protection with the PIN as the user password and a random owner password. PdfBox needs Android classes, so `EncryptedSmsPdfTest` is an instrumented test.
- `SettingsManager` — Preferences DataStore (`settings`). The API key and PDF PIN are stored encrypted (`email_api_key_enc`, `pdf_pin_enc`) via `SecretCipher` (AES-GCM, Android Keystore key), so use `getApiKey()`/`getPdfPin()`, not a Flow. The legacy plaintext `email_api_key` is read as a fallback and removed on the next save. Keystore code only runs on a device, not in JVM unit tests.
- `MainActivity` / `SettingsScreen` — a Compose form with validation, a warning card when `RECEIVE_SMS` is denied, a Send Test Email button and the last-status line. `SettingsScreen` takes the permission state and callbacks as parameters, so tests can call `SettingsScreen(settingsManager)`.

Tests in `app/src/androidTest` need a device and are not run in CI. They write to the app's real DataStore, so a UI test can see values another test saved (`SettingsScreenTest` resets settings in `@Before`). Never run them on a phone with real settings: they overwrite the API key. Use an emulator (`ANDROID_SERIAL=emulator-5554`).

## Conventions

- Commit messages must follow Conventional Commits; CI enforces this with commitlint (`.commitlintrc.yaml`).
- Code style is ktlint (applied to all subprojects from the root `build.gradle`). Composable functions use `@Suppress("FunctionName")` to satisfy it.
- CI (`.github/workflows/ci.yml`) runs on push/PR to `master`: commitlint, then ktlint and unit tests, then the debug build with the APK uploaded as an artifact.
