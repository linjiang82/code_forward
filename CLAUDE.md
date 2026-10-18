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

Lightweight Clean Architecture in one module. **Dependency rule:** `data` and presentation (`ui`, `background`) may depend on `domain`; `domain` depends on neither of them and on no Android API (`android.*`/`androidx.*`), so it is fully unit-tested on the JVM. Only `di` may reference every layer. `ArchitectureTest` enforces this by scanning imports, so a violation fails `testDebugUnitTest`.

```
domain/      pure Kotlin: models, ports (interfaces), SmsProcessor, SettingsValidator, use cases
data/        implementations of the ports: DataStore settings, Resend email, encrypted PDF
background/  Android entry points: SmsReceiver, ForwardWorker, WorkManagerForwardScheduler
ui/          MainActivity (permission only), SettingsViewModel, stateless SettingsScreen
di/          AppContainer: the composition root, created once in SmsForwarderApp
```

Flow: `SmsReceiver` → `SelectMessagesToForwardUseCase` (join multipart PDUs, filter by watched numbers or forward-all) → `ForwardScheduler` (WorkManager: network constraint, exponential backoff) → `ForwardWorker` → `ForwardSmsUseCase` → `EmailSender`.

- **Ports** (`domain/Ports.kt`, `domain/email/EmailSender.kt`): `SettingsRepository`, `EncryptedPdfBuilder`, `ForwardScheduler`, `EmailSender`/`EmailSenderFactory`. Only `di/AppContainer` picks implementations. To switch email provider, add an `EmailSender` in `data/email/` and change the factory there.
- **`ForwardSmsUseCase`** holds the forwarding rules. It chooses plain text or an encrypted PDF (generic subject, no sender or body in clear text). It never falls back to plain text when encryption is on but the PIN or PDF fails. It retries `SendResult.Retryable` (IO errors, 429, 5xx) until `MAX_ATTEMPTS` = 10, and writes the user-visible `last_status`. `ForwardWorker` only maps `ForwardOutcome` to `Result` and passes its work id as the Resend `Idempotency-Key`.
- **`SmsProcessor`**: sender matching (spaces, dashes and parentheses removed; case-insensitive suffix match; targets shorter than `MIN_TARGET_LENGTH` = 4 are ignored; forward-all bypasses the list) and multipart joining.
- **`DataStoreSettingsRepository`**: secrets (`email_api_key_enc`, `pdf_pin_enc`) are encrypted via `SecretCipher` (AES-GCM, Android Keystore key). The legacy plaintext `email_api_key` is read as a fallback and removed on save. It takes a `DataStore` in its constructor so tests can use a throwaway file.
- **`EncryptedSmsPdf`** draws text with Android's `PdfDocument`/`StaticLayout`, so every script and emoji the phone supports works. PdfBox-Android then applies AES-256 with the PIN as the user password.
- **UI**: `SettingsScreen` is stateless (state + callbacks). `SettingsRoute` connects it to `SettingsViewModel`, which handles loading, validation (`SettingsValidator`), saving and the test email.

## Testing

Write the test first and watch it fail before implementing (see the global AGENTS.md rules).

- JVM tests (`app/src/test`) cover the layer rule (`ArchitectureTest`), the domain, the ViewModel (with `kotlinx-coroutines-test`, `Dispatchers.setMain`) and `ResendEmailSender` (OkHttp `MockWebServer`). Fakes for every port are in `test/.../domain/Fakes.kt`.
- Instrumented tests (`app/src/androidTest`) cover Android-bound code: the DataStore repository (throwaway DataStore file), the encrypted PDF (opens only with the right PIN) and the stateless screen (in-memory state). None of them touch the app's real settings. CI doesn't run them; use an emulator (`ANDROID_SERIAL=emulator-5554`).

## Conventions

- Commit messages must follow Conventional Commits; CI enforces this with commitlint (`.commitlintrc.yaml`).
- Code style is ktlint (applied to all subprojects from the root `build.gradle`). Composable functions use `@Suppress("FunctionName")` to satisfy it.
- CI (`.github/workflows/ci.yml`) runs on push/PR to `master`: commitlint, then ktlint and unit tests, then the debug build with the APK uploaded as an artifact.
