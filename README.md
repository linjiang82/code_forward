# SMS to Email Forwarder (Android)

A lightweight, native Android application written in Kotlin that monitors incoming SMS messages from specific phone numbers and automatically forwards them to your email address using the [Resend](https://resend.com) API.

## Features

- **Background Monitoring:** Uses a native Android `BroadcastReceiver` to listen for SMS messages even when the app is not running.
- **Filtering:** Configure a list of target phone numbers (or suffixes of at least 4 characters) to monitor, separated by commas. Spaces, dashes and parentheses are ignored. Or turn on **Forward all messages** to forward every incoming SMS.
- **Encrypted API key:** Settings live in `Preferences DataStore`; the Resend API key is encrypted with an AES key held in the Android Keystore, and app backup is disabled.
- **Reliable delivery:** Emails are sent through WorkManager, which waits for network and retries transient failures (network errors, HTTP 429/5xx) with exponential backoff, up to 10 attempts.
- **Encrypted PDF option:** Turn on **Encrypt as PDF attachment** and set a PIN. Each SMS is then sent as an AES-256 password-protected PDF, and the email itself has a generic subject ("New SMS") and no message text. Any standard PDF viewer (iPhone/Mac Preview, Chrome, Adobe) asks for the PIN. Gmail's in-browser preview can't open protected PDFs, so download the file or open it in the Gmail app's viewer. A short PIN keeps the codes out of plain sight in Resend's logs, your mailbox and notifications, but someone who obtains the PDF can try every 4-digit PIN in under a second.
- **Status feedback:** The settings screen shows the result of the most recent forward and has a **Send Test Email** button.
- **Modern UI:** Built with Jetpack Compose for a clean and responsive configuration experience.
- **CI/CD Integrated:** Automated linting, testing, and building via GitHub Actions.

## How It Works

1.  **SmsReceiver:** A system-level listener that catches the `SMS_RECEIVED` intent and hands the message parts to the domain layer.
2.  **SelectMessagesToForwardUseCase:** Joins multipart messages and keeps those from watched numbers (or all, with forward-all on).
3.  **ForwardWorker:** A WorkManager job per message that waits for network, retries with backoff, and runs **ForwardSmsUseCase**, which builds the plain or encrypted-PDF email and records the outcome.
4.  **EmailSender:** Interface for the email provider. `ResendEmailSender` is the current implementation; `di/AppContainer.kt` is the only place that chooses it.

The code follows a lightweight Clean Architecture (`domain`, `data`, `background`, `ui`, `di`). See `CLAUDE.md` for the layer rules.

## Setup Instructions

### Prerequisites
- An Android device or emulator (API level 24+).
- A **Resend API key** (`re_...`) from https://resend.com/api-keys.
- A domain verified in Resend to send from. For a quick test without a domain, use `onboarding@resend.dev` as the From address; Resend then only delivers to the email address of your Resend account.
- The app is meant to be sideloaded. Google Play only allows SMS permissions for default SMS apps and a few approved use cases, so it is unlikely to be accepted there.

### Configuration
1.  Launch the app and grant the requested **SMS Permissions**.
2.  **Forward from Numbers:** Enter one or more phone numbers you want to monitor, separated by commas (e.g., `+1234567890, +9876543210` or just the last few digits), or turn on **Forward all messages**.
3.  **Resend API Key:** Paste your secret API key.
4.  **Email Addresses:** Enter the "To" (destination) and "From" (an address on your verified Resend domain, or `onboarding@resend.dev`) email addresses.
5.  Optionally turn on **Encrypt as PDF attachment** and enter a PIN of at least 4 digits.
6.  Click **Save Configuration**, then **Send Test Email** and check the status line.

## Development

### Running Locally
The build uses Gradle 8.3, which needs **JDK 17** (newer JDKs such as the one bundled with recent Android Studio fail with "Unsupported class file major version"). Point `JAVA_HOME` at a JDK 17 and create `local.properties` with your `sdk.dir`, then:
```bash
./gradlew assembleDebug
```

### Release builds
`./gradlew assembleRelease` signs the APK when these Gradle properties (e.g. in `~/.gradle/gradle.properties`) or environment variables are set: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Otherwise it produces an unsigned APK.

### Testing
The project follows a TDD approach with Unit and UI tests.
```bash
# Run unit tests
./gradlew test

# Run linting checks
./gradlew ktlintCheck

# Run instrumented / UI tests (device or emulator required)
./gradlew connectedDebugAndroidTest
```

### CI/CD Pipeline
Every push to the repository triggers a GitHub Action that:
- Lints the commit messages (Conventional Commits).
- Checks code formatting with `ktlint`.
- Runs unit tests.
- Builds a debug APK and uploads it as an artifact.

## Security Note
The API key is encrypted at rest with a non-exportable Android Keystore key, and `android:allowBackup` is off, so it is not copied into cloud backups. SMS content is only handed to WorkManager for messages from watched numbers.

## License
MIT
