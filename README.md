# Cloud ACR – Android Call Recording App

A fully functional call recording app for **Android 16** (API 36) that mimics the architecture and UX of Cloud ACR. Includes:

- **`app/`** – Main Cloud ACR app: auto-records calls, manages recordings library
- **`helper/`** – Cloud ACR Helper: Accessibility Service companion for improved recording reliability

---

## Architecture

```
CloudACR (main app)               CloudACR Helper
├── PhoneStateReceiver ──────►   ACRAccessibilityService
│   (listens for call state)      (monitors dialer UI)
│                                 │
├── CallRecordingService ◄────────┘
│   (MediaRecorder foreground      (signed broadcast)
│    service, AAC/M4A output)
│
├── Room Database
│   └── Recording entity
│
└── MainActivity + Adapter
    RecordingDetailActivity
    SettingsActivity
```

### Why Two Apps?

On **Android 9+** (and especially Android 13/14/16), the `VOICE_CALL` audio source is restricted by default. Running the helper as a **separate APK with an Accessibility Service** is the same technique used by Cube ACR, ACR Phone, and other production recorders. The helper detects call state by inspecting the dialer UI accessibility tree, then sends a **signature-protected broadcast** to the main app to start/stop recording.

---

## Features

### Main App (Cloud ACR)
| Feature | Details |
|---------|---------|
| Auto call recording | Triggers on every incoming + outgoing call |
| Foreground service | Persistent recording with wakelock |
| M4A / MP4 / 3GP output | User-configurable in settings |
| Room database | Stores all metadata; persists across reboots |
| Search & filter | Full-text search; starred filter |
| Bulk delete | Long-press multi-select |
| In-app audio player | SeekBar, play/pause, elapsed time |
| Per-recording notes | Editable and saved to DB |
| Transcription field | Ready for future ASR integration |
| Share recording | Intent share via FileProvider |
| Boot-start | Re-arms on device reboot |
| Helper integration | Detects helper presence, shows badge |

### Helper App (Cloud ACR Helper)
| Feature | Details |
|---------|---------|
| Accessibility Service | Monitors dialer window events |
| Multi-OEM support | Covers Google, Samsung, OnePlus, Huawei, Xiaomi, Oppo dialers |
| Call state detection | Looks for "End call" button + TelephonyManager fallback |
| Phone number extraction | Reads from known view IDs in dialer |
| Signed broadcast | `signature` permission, only main app can receive |
| Setup UI | Shows service status, links to Accessibility Settings |
| Boot safe | Auto-restarts after reboot if enabled |

---

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 36 installed

### Steps
```bash
# Clone / open in Android Studio
git clone <repo>
cd CloudACR

# Build debug APKs
./gradlew :app:assembleDebug
./gradlew :helper:assembleDebug

# Install both on device
adb install app/build/outputs/apk/debug/app-debug.apk
adb install helper/build/outputs/apk/debug/helper-debug.apk
```

> ⚠️ Both APKs must be signed with the **same key** for the `signature`-level permission to work between them. In debug builds, both are signed with the debug keystore automatically.

---

## Permissions

### Main App
| Permission | Why |
|-----------|-----|
| `RECORD_AUDIO` | Record microphone during calls |
| `READ_PHONE_STATE` | Detect call state changes |
| `READ_CALL_LOG` | Access call history for metadata |
| `FOREGROUND_SERVICE_MICROPHONE` | Required for Android 14+ mic foreground service |
| `POST_NOTIFICATIONS` | Show recording notification (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm after reboot |

### Helper App
| Permission | Why |
|-----------|-----|
| `BIND_ACCESSIBILITY_SERVICE` | Core helper functionality |
| `READ_PHONE_STATE` | TelephonyManager call state fallback |

---

## Android 16 Notes

Android 16 (API 36) tightens several restrictions:

1. **Audio source restrictions**: `VOICE_CALL` source may be silenced; the app uses `VOICE_COMMUNICATION` first with a `VOICE_CALL` fallback.
2. **Foreground service types**: `foregroundServiceType="microphone"` is required and declared in the manifest.
3. **Broadcast receivers**: All dynamic receivers use `RECEIVER_NOT_EXPORTED` flag.
4. **Notification permission**: `POST_NOTIFICATIONS` is requested at runtime.
5. **Predictive Back**: The app uses `parentActivityName` for proper back navigation.

---

## File Structure

```
CloudACR/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/cloudacr/app/
│       │   ├── CloudACRApp.kt          # Application class, DI root
│       │   ├── data/
│       │   │   ├── Recording.kt        # Room entity
│       │   │   ├── RecordingDao.kt     # Room DAO
│       │   │   ├── AppDatabase.kt      # Room database
│       │   │   └── RecordingRepository.kt
│       │   ├── service/
│       │   │   ├── CallRecordingService.kt  # Foreground recording service
│       │   │   ├── PhoneStateReceiver.kt    # Phone state broadcast receiver
│       │   │   └── Receivers.kt             # Boot + Helper command receivers
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── MainViewModel.kt
│       │   │   ├── RecordingAdapter.kt
│       │   │   ├── RecordingDetailActivity.kt
│       │   │   └── SettingsActivity.kt
│       │   └── utils/
│       │       ├── StorageUtils.kt
│       │       ├── ContactUtils.kt
│       │       └── AudioUtils.kt
│       └── res/
│           ├── layout/         # XML layouts
│           ├── drawable/       # Vector icons + backgrounds
│           ├── menu/           # Options menus
│           ├── values/         # Strings, colors, themes, arrays
│           └── xml/            # Preferences XML
│
└── helper/
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/cloudacr/helper/
        │   ├── ACRAccessibilityService.kt  # Core accessibility service
        │   ├── HelperMainActivity.kt       # Setup / status UI
        │   └── HelperBootReceiver.kt
        └── res/
            ├── layout/
            ├── drawable/
            ├── values/
            └── xml/
                └── accessibility_service_config.xml
```

---

## Legal & Privacy Note

Call recording laws vary by country and state. This app is provided for **educational/personal use only**. Always inform parties before recording a call where required by law.
