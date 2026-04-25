# SMS to Email Forwarder (Android)

A lightweight, native Android application written in Kotlin that monitors incoming SMS messages from specific phone numbers and automatically forwards them to your email address using the SendGrid API.

## Features

- **Background Monitoring:** Uses a native Android `BroadcastReceiver` to listen for SMS messages even when the app is not running.
- **Filtering:** Configure a list of target phone numbers (or suffixes) to monitor, separated by commas.
- **Secure Storage:** Sensitive API keys and configurations are stored using modern Android `Preferences DataStore`.
- **Modern UI:** Built with Jetpack Compose for a clean and responsive configuration experience.
- **CI/CD Integrated:** Automated linting, testing, and building via GitHub Actions.

## How It Works

1.  **SmsReceiver:** A system-level listener that catches the `SMS_RECEIVED` intent.
2.  **SmsProcessor:** Pure business logic that checks if the sender matches your configuration and builds the JSON payload.
3.  **Forwarding:** If a match is found, the app makes a direct REST API call to SendGrid to send the email.

## Setup Instructions

### Prerequisites
- An Android device or emulator (API level 24+).
- A **SendGrid API Key** (or you can modify `SmsReceiver.kt` to use another provider).

### Configuration
1.  Launch the app and grant the requested **SMS Permissions**.
2.  **Forward from Numbers:** Enter one or more phone numbers you want to monitor, separated by commas (e.g., `+1234567890, +9876543210` or just the last few digits).
3.  **SendGrid API Key:** Paste your secret API key.
4.  **Email Addresses:** Enter the "To" (destination) and "From" (verified sender in SendGrid) email addresses.
5.  Click **Save Configuration**.

## Development

### Running Locally
You can build the project using the included Gradle wrapper:
```bash
./gradlew assembleDebug
```

### Testing
The project follows a TDD approach with Unit and UI tests.
```bash
# Run unit tests
./gradlew test

# Run linting checks
./gradlew ktlintCheck
```

### CI/CD Pipeline
Every push to the repository triggers a GitHub Action that:
- Lints the commit messages (Conventional Commits).
- Checks code formatting with `ktlint`.
- Runs unit tests.
- Builds a debug APK and uploads it as an artifact.

## Security Note
API keys are stored locally on the device's storage. Access to this data is restricted by the Android operating system to this specific application.

## License
MIT
