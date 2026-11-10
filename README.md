<img width="640" height="640" alt="photo_2026-10-17_19-50-32" src="https://github.com/user-attachments/assets/7140bd39-ce45-4b4f-afb4-93236b67ac36" />

# 🎮 DollXRStore

<p align="center">
  <strong>An Android game store for the DollXR platform.</strong>
  <br>
  Discover games, browse the catalog, and download DXR files directly to your device.
</p>

<p align="center">
  <a href="https://t.me/dollxr_offical">✈️ Official Telegram Channel</a>
  ·
  <a href="https://t.me/dollxr_project">💬 Community Forum</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-HTML%20%2F%20CSS%20%2F%20JS-E34F26?logo=html5&logoColor=white" alt="HTML CSS JS">
  <img src="https://img.shields.io/badge/Part%20of-DollXR-FF69B4" alt="DollXR">
</p>

<p align="center">
  **Created by D0ll1ii** 💗
</p>

---

## 📖 About

DollXRStore is an Android game store interface built with Kotlin and an HTML/CSS/JavaScript UI hosted inside Android WebView.

It includes Google sign-in through Firebase and an optional private GitHub game catalog.

The application is part of the **DollXR ecosystem**, designed to make discovering and downloading games easier.

## ✨ Features

- **Native Android architecture** — Android shell with the existing WebView and `DollXRBridge` architecture.
- **Responsive interface** — DollXRStore UI built with HTML, CSS, and JavaScript in `app/src/main/assets/index.html`.
- **Google Authentication** — Credential Manager with the existing Google Sign-In fallback and Firebase Authentication.
- **Private GitHub catalog** — GitHub App Device Flow for accessing a private games repository.
- **Secure token storage** — GitHub API calls and game downloads run in Kotlin. Access and refresh tokens are encrypted with AES-GCM using Android Keystore.
- **Game cards** — icons, descriptions, optional banners, and search.
- **DXR downloads** — download game files to `Download/DollXRStore`.

### 🔐 Security and permissions

The app only requests GitHub repository permission `Contents: Read-only`.

It does not include:
- A GitHub client secret.
- A GitHub private key.
- A personal access token.
- A Firebase backend for GitHub.
- A separate DollXR backend.

## 📂 Project Structure

```text
DollXRStore/
├── app/
│   ├── src/main/
│   │   ├── assets/       # HTML/CSS/JavaScript interface
│   │   ├── java/         # Kotlin application code
│   │   └── res/          # Android resources
│   └── build.gradle.kts
├── gradle/               # Gradle Wrapper and configuration
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties.example
└── README.md
```

### Main directories

- `app/src/main/java/` — Android Activity, WebView bridge, Google authentication, GitHub integration, and Kotlin app code.
- `app/src/main/assets/` — HTML/CSS/JavaScript interface and bundled UI assets.
- `app/src/main/res/` — Android layouts, themes, icons, and strings.
- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/` — Android and Gradle configuration, version catalog, and Gradle Wrapper.
- `local.properties.example` — local-only developer settings template.

**Application ID:** `com.d0ll1ii.dollxrstore`

## ⚙️ Requirements

- Android Studio with Android SDK Platform 37 and a compatible JDK. The included Gradle daemon criteria request JDK 25.
- A Firebase project and Google OAuth Web client ID for Google sign-in.
- A GitHub App with Device Flow enabled for private games access.

### Build environment

| Component | Version |
|---|---|
| Gradle Wrapper | 9.6.0 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.1.0 |
| Compile SDK | 37 |
| Minimum SDK | 29 |
| JDK | 25 (Gradle daemon criteria) |

Gradle downloads SDK dependencies as needed.

## 🛠️ Local Development Setup

1. Copy `local.properties.example` to `local.properties`.
2. Set `sdk.dir` to your Android SDK path.
3. Add the Google Web client ID and GitHub App/repository values described below.
4. Obtain your own `app/google-services.json` from Firebase Console.

Place the file at `app/google-services.json`.

This file is intentionally excluded from the source package and Git. Do not use another developer's Firebase configuration.

### Important security notes

`local.properties` and `app/google-services.json` are ignored by Git.

**Never commit:**
- Signing keys.
- OAuth client secrets.
- Firebase service account keys.
- User access tokens.

## 🔑 Google Sign-In and Firebase Setup

1. Create a Firebase project, then add an Android application with package name `com.d0ll1ii.dollxrstore`.
2. In Firebase Authentication, enable the Google provider.
3. Add the SHA-1 fingerprints for your debug and release signing certificates to the Firebase Android app settings. Run `gradlew signingReport` to view local fingerprints.
4. Download the Android `google-services.json` for your Firebase app and place it at `app/google-services.json`.
5. Find the OAuth 2.0 **Web application** client ID from your Firebase/Google Cloud project and set `dollxr.google.webClientId` in `local.properties`.

The Firebase and Google SDK dependencies and the real sign-in flow remain in the project.

If the Web client ID is empty, sign-in reports a configuration error. It is not replaced with a mock login.

## 🐙 GitHub Games Repository Setup

Create your own GitHub App from:

**GitHub Settings → Developer settings → GitHub Apps**

1. Enable **Device Flow**.
2. Set repository permission `Contents` to **Read-only** and leave unrelated permissions disabled.
3. Install the app using **Only select repositories** and choose only your games repository.
4. Put the GitHub App **Client ID** (not the App ID, client secret, or private key), repository owner, and repository name in `local.properties`:

```properties
dollxr.github.appClientId=YOUR_GITHUB_APP_CLIENT_ID
dollxr.github.owner=YOUR_GITHUB_USERNAME_OR_ORG
dollxr.github.repo=YOUR_GAMES_REPOSITORY
```

Client IDs are public identifiers.

Do not put a GitHub client secret or private key in the APK.

User access and refresh tokens are held in encrypted app-private storage protected by Android Keystore.

Users can revoke the GitHub App authorization in their GitHub settings.

### 🎮 Game Files

The app lists ready-to-download `.dxr` files from the root of `games/`.

A separate folder per game is supported:

```text
games/
└── Neon Strike/
    ├── Neon Strike.dxr
    ├── icon.png
    ├── description.txt
    └── banner.png
```

If the game folder contains exactly one `.dxr`, it may have a different filename.

The flat layout is also supported:

```text
games/
├── NAME.dxr
├── NAME.png
├── NAME.txt
└── NAME-banner.png
```

Images and descriptions are fetched through the authenticated Android GitHub API.

The game file is saved to the device's:

```text
Download/DollXRStore
```

## 🏗️ Build an APK

After adding your own `local.properties` and `app/google-services.json`, open the project root in Android Studio and sync Gradle.

You can also build a debug APK from the command line.

**Windows:**

```bash
gradlew.bat :app:assembleDebug
```

**macOS / Linux:**

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release signing is not configured in this source package.

Create and protect your own signing key outside the repository before publishing a release.

## 📜 License

DollXRStore is distributed under the **DollXR Community License (DCL)**.

Please read the [`LICENSE`](LICENSE) file in this repository before using, modifying, or redistributing the project.

The license requires attribution to **D0ll1ii** and compliance with its terms.

## 💬 Community

Stay connected with the DollXR project:

- ✈️ [Official Telegram Channel](https://t.me/dollxr_offical)
- 💬 [Community Forum](https://t.me/dollxr_project)

---

<p align="center">
  <strong>Made with 💗 by D0ll1ii</strong>
  <br>
  <sub>Part of the DollXR ecosystem</sub>
</p>
