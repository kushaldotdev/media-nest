# MediaNest

MediaNest is an Android-first, offline-centric media library application designed for personal media consumption and organization.

It allows you to download YouTube videos and audio locally, organize them into folders, maintain playback history, and optionally sync your metadata across devices.

## Features

- **Offline-First**: Functions completely without internet access after content is downloaded.
- **Local Media Storage**: Videos and audio are stored directly on your device storage.
- **YouTube Downloading**: Extract and download videos, audio, playlists, and channel uploads using `NewPipeExtractor`.
- **Media Playback**: Full background audio and video playback using ExoPlayer.
- **Organization**: Organize media into nested folders, tags, and favorites.
- **Optional VPS Sync**: Keep watch history, subscriptions, and settings synced across devices (metadata only, no media blobs).

---

## Building the APKs

This project uses Gradle. Since this is a Windows environment, use the provided wrapper script (`gradlew.bat`) or the custom `build.bat` script in the root directory.

### Debug Build
To build a debug APK for testing:
```cmd
.\gradlew.bat :app:assembleDebug
```
*Alternatively, you can run `.\build.bat clean` to perform a clean debug build.*

The generated APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
To build a release APK:
```cmd
.\gradlew.bat :app:assembleRelease
```
This generates an **unsigned** APK at: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## GitHub Release & APK Signing

Android requires all APKs to be digitally signed with a certificate before they can be installed. This ensures the app's integrity and verifies your identity for future updates.

### 1. Sign the Release APK
1. **Generate a Keystore**: Use Android Studio's "Generate Signed APK" wizard or the `keytool` CLI to create a `.jks` keystore file.
2. **Sign the APK**: Sign the `app-release-unsigned.apk` using `apksigner` (found in the Android SDK build-tools).
   *(Note: You can automate this process in the future by adding a `signingConfigs { release { ... } }` block to `app/build.gradle.kts`)*.

### 2. Create a GitHub Release
1. Navigate to your project's GitHub repository page.
2. Click on **Releases** on the right sidebar, then click **Draft a new release**.
3. Create a new tag (e.g., `v1.0.0`) that matches the `versionName` of your build.
4. Fill in the release title and add a description detailing the changelog and new features.
5. **Upload the APK**: Drag and drop your **signed** `.apk` file into the assets upload box.
6. Click **Publish release**.

---

## Hosting the Optional Sync Server (VPS)

While the app functions perfectly completely offline, you can optionally host a sync server on a Virtual Private Server (VPS) to synchronize your metadata (subscriptions, history, settings) across multiple devices.

This project includes a custom Python FastAPI implementation located in the `sync-server/` directory.

### Method A: Docker (Recommended)
The easiest way to host the server is using Docker.
1. Copy the `sync-server/` directory to your VPS.
2. Navigate into the directory and start the container:
   ```bash
   cd sync-server
   docker-compose up -d
   ```
This will start the sync server on port `8000`.

### Method B: Bare Metal / Systemd
If you prefer running it natively without Docker:
1. Copy the `sync-server/` directory to your VPS.
2. Run the provided setup script:
   ```bash
   cd sync-server
   ./deploy/setup.sh
   ```
This script installs the `uv` package manager, sets up a dedicated user, and configures a `medianest-sync` systemd service running on port `8000`.

### Finalizing the VPS Setup
For both methods, you should set up a reverse proxy (such as Nginx or Caddy) to expose port `8000` to the internet via a custom domain. Always secure your domain with an SSL certificate (e.g., Let's Encrypt) to ensure your sync traffic is encrypted.
