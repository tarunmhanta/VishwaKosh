# VishwaKosh 📦
### Unlimited, Secure Cloud Media Backup Powered by Telegram

VishwaKosh is a premium offline-first Android gallery and cloud vault application built entirely using **Kotlin, Jetpack Compose, and Material 3**. It offers users a private, secure, and unlimited cloud backup solution by leveraging the Telegram Bot API to store photos and videos in private Channels.

---

## 🎨 Visual Preview & UI Wireframes

### Local Gallery Grid
```text
┌────────────────────────────────────────────────────────┐
│  TeleBox 📦  [Local]   [Cloud]   [Settings]            │
├────────────────────────────────────────────────────────┤
│  Ready to upload? Select multiple to begin.            │
│                                                        │
│  [ June 29, 2026 ]                                     │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │   [📷]    │ │   [📷]    │ │   [🎥]    │ │   [📷]    │ │
│  │  Photo 1  │ │  Photo 2  │ │  Video 1  │ │  Photo 3  │ │
│  │           │ │           │ │   00:45   │ │           │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  ● 3 Selected   Ready to upload        [UPLOAD]  │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### Sleek Inline Video Player & Detail Viewer
```text
┌────────────────────────────────────────────────────────┐
│  ◀  Media Details                      [Backup Status] │
├────────────────────────────────────────────────────────┤
│                                                        │
│                    ┌───────────────┐                   │
│                    │     [▶]       │                   │
│                    │               │                   │
│                    │  Custom Video │                   │
│                    │   Playback    │                   │
│                    │               │                   │
│                    └───────────────┘                   │
│  ────────────────────────────────────────────────────  │
│  [  ▶  ] 00:15 ────────────────────────●─────── 00:45  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │             [ BACKUP TO TELEGRAM ]               │  │
│  ├──────────────────────────────────────────────────┤  │
│  │             [ PLAY IN EXTERNAL PLAYER ]          │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### Cloud Gallery (with Multi-Select Deletion)
```text
┌────────────────────────────────────────────────────────┐
│  TeleBox 📦  [Local]   ● [Cloud]   [Settings]          │
│  ────────────────────────────────────────────────────  │
│  Search cloud by name or date (yyyy-mm-dd)      [🔍]  │
│                                                        │
│  [ Today ]                                 Deselect    │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │   [✓]     │ │   [✓]     │ │   [ ]     │ │   [ ]     │ │
│  │   [📷]    │ │   [🎥]    │ │   [📷]    │ │   [📷]    │ │
│  │  Photo 1  │ │  Video 1  │ │  Photo 2  │ │  Photo 3  │ │
│  │  [Cloud]  │ │  [Cloud]  │ │  [Cloud]  │ │  [Cloud]  │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  ● 2 Selected   Delete from Cloud      [DELETE]  │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## ✨ Features

- **🚀 Smart Gallery & Batch Backup**: Scroll through your entire local camera roll, select multiple items seamlessly, and upload them in a single tap with status bar indicators.
- **🎥 Native Video Thumbnails & Frame Extraction**: Features high-fidelity video frame decoding powered by `coil-video`. Every video file shows its real content frame as a thumbnail instead of a generic placeholder.
- **⚡ Custom High-Fidelity Video Player**: Enjoy your videos inline! Includes a custom playback engine complete with Play/Pause toggles, a drag-to-seek progress slider, elapsed time indicators, and full screen support.
- **🍿 External Player Companion**: A high-compatibility option to open videos instantly in your phone's default multimedia player (MX Player, VLC, Photos, etc.) with read permissions automatically handled.
- **💾 Local Space Reclamation**: Backed up files display a cloud badge. Once backed up, easily delete the local file from within the app using the "Free Up Space" utility—saving gigabytes of device storage.
- **🗑️ Dual Cloud Media Deletion**: Fully supports deleting single media items from the detail screen as well as multiple items at once using the Cloud Gallery long-press multi-select action bar. This deletes the message directly from your private Telegram storage channel and seamlessly updates the local app database.
- **🔒 Encrypted/Private Vault**: Built-in interactive setup screen guiding you to configure your own custom Telegram Bot and Private Storage Channel. Your media never passes through third-party servers.

---

## 🛠️ Tech Stack & Architecture

TeleBox utilizes **Modern Android Development (MAD)** standards for optimal performance, responsiveness, and clean code:

*   **UI Framework:** [Jetpack Compose](https://developer.android.com/compose) with Material 3 design and dynamic color palettes.
*   **Database:** [Room Database](https://developer.android.com/training/data-storage/room) acting as an offline metadata cache for instant launch times.
*   **Media Loading:** [Coil Compose](https://coil-kt.github.io/coil/) with `Coil Video` decoder to stream high-quality local and remote images and video frames.
*   **Concurrency:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [StateFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/) for background thread operations, upload progress indicators, and seamless reactive state changes.
*   **Network Service:** Custom clean Telegram API service to handle secure multi-part form uploads up to 2GB.

---

## 🚀 Easy 4-Step Storage Vault Setup

To configure TeleBox for unlimited cloud storage, complete these quick configuration steps:

1.  **Create Your Telegram Bot**:
    *   Open Telegram and search for `@BotFather`.
    *   Send `/newbot` and follow the steps to receive your unique **Bot Token** (e.g. `123456:ABC-DEF1234...`).
2.  **Create Your Storage Channel**:
    *   In Telegram, create a **New Channel** and set it to **Private**.
3.  **Add Your Bot as Admin**:
    *   Go to the Channel settings -> Administrators -> Add Admin.
    *   Search for your bot's username and grant it administrative permission to **Post Messages**.
4.  **Obtain Your Channel ID**:
    *   Forward any message from your newly created channel to `@userinfobot` or any ID finder bot to retrieve your Chat ID (a negative integer starting with `-100...`).
5.  **Configure TeleBox**:
    *   Open TeleBox, click on the **Settings** icon/tab, insert your **Bot Token** and **Channel ID**, and tap **Save & Connect**. You are now ready to back up your media!

---

## 🏗️ Building and Compiling the Project

Ensure you have Android Studio installed.

### Dependencies
All core dependencies are managed through the centralized Gradle Version Catalog `gradle/libs.versions.toml`:
- Compose Material 3: `androidx.compose.material3`
- Room Persistence: `androidx.room`
- Coil Image & Video Loader: `coil-compose` & `coil-video`
- Accompanist Permissions: `accompanist-permissions`

### Build Command
Compile the debug application APK:
```bash
gradle assembleDebug
```

---

## 🎖️ Developer Credits

Developed with ♥️ by the **Tarun Mhanta** on Google AI Studio. 

Designed using the **Professional Polish** Material 3 visual guidelines, offering eye-friendly color pairings, comfortable negative space, premium components, and an unlimited file management system.

---
*Disclaimer: TeleBox is an open-source backup tool that interacts directly with the official Telegram API. We do not host your files. All files are securely kept in your private Telegram storage cloud.*
