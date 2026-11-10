# DollXRStore

DollXRStore is an Android game store interface built with Kotlin and an HTML/CSS/JavaScript UI hosted inside Android WebView. It includes Google sign-in through Firebase and an optional private GitHub game catalog.

## Features

- Native Android shell with the existing WebView and `DollXRBridge` architecture.
- Responsive DollXRStore interface in `app/src/main/assets/index.html`.
- Google Authentication using Credential Manager with the existing Google Sign-In fallback and Firebase Authentication.
- GitHub App Device Flow for a private games repository. GitHub API calls and game downloads run in Kotlin; access and refresh tokens are encrypted with AES-GCM using Android Keystore.
- Game cards with icons, descriptions, optional banners, search, and DXR downloads to `Download/DollXRStore`.

The app only requests GitHub repository `Contents: Read-only`. It does not include a GitHub client secret, private key, personal access token, Firebase backend for GitHub, or a separate DollXR backend.

## Project structure

- `app/src/main/java/` — Android Activity, WebView bridge, Google authentication, GitHub integration, and Kotlin app code.
- `app/src/main/assets/` — HTML/CSS/JavaScript interface and bundled UI assets.
- `app/src/main/res/` — Android layouts, themes, icons, and strings.
- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/` — Android and Gradle configuration, version catalog, and Gradle Wrapper.
- `local.properties.example` — local-only developer settings template.

The Android application ID is `com.d0ll1ii.dollxrstore`.

## Requirements

- Android Studio with Android SDK Platform 37 and a compatible JDK. The included Gradle daemon criteria request JDK 25.
- A Firebase project and Google OAuth Web client ID for Google sign-in.
- A GitHub App with Device Flow enabled for private games access.

The project uses Gradle Wrapper 9.6.0, Android Gradle Plugin 9.5.1, Kotlin 2.1.0, compile SDK 37, and min SDK 29. Gradle downloads SDK dependencies as needed.

## Configure your local development environment

1. Copy `local.properties.example` to `local.properties`.
2. Set `sdk.dir` to your Android SDK path.
3. Add the Google Web client ID and GitHub App/repository values described below.
4. Obtain your own `app/google-services.json` from Firebase Console. This file is intentionally excluded from the source package and Git. Do not use another developer's Firebase configuration.

`local.properties` and `app/google-services.json` are ignored by Git. Never commit signing keys, OAuth client secrets, Firebase service account keys, or user access tokens.

## Google Sign-In and Firebase setup

1. Create a Firebase project, then add an Android application with package name `com.d0ll1ii.dollxrstore`.
2. In Firebase Authentication, enable the Google provider.
3. Add the SHA-1 fingerprints for your debug and release signing certificates to the Firebase Android app settings. Run `gradlew signingReport` to view local fingerprints.
4. Download the Android `google-services.json` for your Firebase app and place it at `app/google-services.json`.
5. Find the OAuth 2.0 **Web application** client ID from your Firebase/Google Cloud project and set `dollxr.google.webClientId` in `local.properties`.

The Firebase and Google SDK dependencies and the real sign-in flow remain in the project. If the Web client ID is empty, sign-in reports a configuration error; it is not replaced with a mock login.

## GitHub games repository setup

Create your own GitHub App from **GitHub Settings → Developer settings → GitHub Apps**:

1. Enable **Device Flow**.
2. Set repository permission **Contents** to **Read-only** and leave unrelated permissions disabled.
3. Install the app using **Only select repositories** and choose only your games repository.
4. Put the GitHub App **Client ID** (not the App ID, client secret, or private key), repository owner, and repository name in `local.properties`:

   ```properties
   dollxr.github.appClientId=YOUR_GITHUB_APP_CLIENT_ID
   dollxr.github.owner=YOUR_GITHUB_USERNAME_OR_ORG
   dollxr.github.repo=YOUR_GAMES_REPOSITORY
   ```

Client IDs are public identifiers. Do not put a GitHub client secret or private key in the APK. User access and refresh tokens are held in encrypted app-private storage protected by Android Keystore. Users can revoke the GitHub App authorization in their GitHub settings.

### Game files

The app lists ready-to-download `.dxr` files from the root of `games/`. A separate folder per game is supported:

```text
games/
└── Neon Strike/
    ├── Neon Strike.dxr
    ├── icon.png
    ├── description.txt
    └── banner.png          # optional
```

If the game folder contains exactly one `.dxr`, it may have a different filename. The flat layout is also supported: `games/NAME.dxr`, `games/NAME.png`, `games/NAME.txt`, and optional `games/NAME-banner.png`. Images and descriptions are fetched through the authenticated Android GitHub API. The game file is saved to the device's `Download/DollXRStore` folder.

## Build an APK

After adding your own `local.properties` and `app/google-services.json`, open the project root in Android Studio and sync Gradle. To build a debug APK from the command line:

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Release signing is not configured in this source package; create and protect your own signing key outside the repository before publishing a release.

## License

No license has been added because no license was provided or agreed. Choose a license before publishing if you want to grant permissions to reuse or redistribute this code.
