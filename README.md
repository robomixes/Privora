# Privora

**Private AI Camera & Encrypted Vault**

Privora is a privacy-first Android app that combines an AI-powered camera, encrypted vault, secure notes, and personal data tools — all running entirely on your device. No cloud. No analytics. No telemetry.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-blue.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](#license)

---

## Features

### Camera
- Photo and video capture with real-time face detection
- Hold-to-record quick video
- AI object detection (YOLOv8n, fully offline)
- Level indicator for perfect alignment
- Photo editor with crop, rotate, filters, text, and stickers

### Encrypted Vault
- AES-256-GCM encryption with hardware-backed keys (TEE/StrongBox)
- 6 categories: Camera, Videos, Scans, Detections, Reports, Files
- Custom folders and subfolders
- Search across all vault items
- Built-in video player with seek and tap-to-pause
- Import photos, videos, and PDFs with automatic EXIF stripping

### Secure Notes
- Encrypted note-taking with 9 color themes
- Pin, tag, and search notes
- Multi-select with bulk operations

### Document Scanner
- Multi-page scanning (up to 10 pages) via ML Kit
- OCR text extraction
- Save as encrypted PDF (A4, 150 DPI)

### QR & Barcode Scanner
- Live scanning with 50-item history
- Context-aware actions (URL, phone, email, Wi-Fi)

### Translation
- Offline translation between 15 languages
- Camera and gallery input with text overlay
- Text-to-speech support

### Personal Insights
- **Expenses** — category tracking, pie charts, month navigation, PDF/CSV export
- **Health** — family profiles, weight, heart rate, blood pressure, sleep, mood, temperature, steps, trend charts
- **Habits** — daily checklist, streak counter, calendar view, completion tracking

### Tools
- Unit converter with 8 categories: length, weight, temperature, speed, data, area, volume, time

---

## Security

| Feature | Details |
|---------|---------|
| Encryption | AES-256-GCM with 12-byte IV and 128-bit tag |
| Key Storage | Two-layer architecture: KEK in Android Keystore (TEE/StrongBox) wraps DEK |
| Authentication | Biometric (fingerprint/face) or app PIN |
| Emergency PIN | Shows empty vault (EMPTY_ONLY) or wipes all data (WIPE mode) |
| EXIF Stripping | GPS, device info, timestamps removed from all shared images |
| Face Blur | Automatic face detection and pixelation before sharing |
| Screenshot Protection | FLAG_SECURE blocks screenshots and screen recording |
| Auto-lock | Configurable grace period (immediate to 2 minutes) |
| Backup | Encrypted .paicbackup format with PBKDF2-SHA256 (600K iterations) |

---

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Camera**: CameraX (Preview + VideoCapture + ImageAnalysis)
- **AI**: ONNX Runtime (YOLOv8n for object detection)
- **ML Kit**: Document scanner, OCR, barcode scanning, translation, face detection
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)

---

## Architecture

```
Privora
├── UI Layer (Jetpack Compose)
│   ├── 13 screens with Material Design 3
│   └── NavHost with 14 routes
├── Security Layer
│   ├── CryptoManager (AES-256-GCM)
│   ├── KeyManager (Android Keystore)
│   ├── VaultRepository (encrypted photo/video storage)
│   ├── NoteRepository (encrypted notes)
│   ├── InsightsRepository (encrypted personal data)
│   ├── FolderManager (custom vault folders)
│   ├── DuressManager (emergency PIN)
│   └── BackupManager (encrypted backup/restore)
├── AI Layer
│   └── OnnxDetector (YOLOv8n, 640x640 input)
├── Camera Layer (CameraX)
│   └── Photo, video, face detection
└── Service Layer
    ├── CrashHandler (local logging)
    ├── DeviceProfiler (performance benchmarking)
    └── StorageManager (size tracking)
```

---

## Localization

| Language | Status |
|----------|--------|
| English | Complete |
| Arabic (RTL) | Complete |
| Spanish | Complete |
| French | Complete |

In-app language switcher available in Settings.

---

## Privacy Policy

See [PRIVACY.md](PRIVACY.md) for our full privacy policy.

**TL;DR**: All AI processing runs on your device. All data is AES-256-GCM encrypted with a per-install key stored in your phone's hardware security module. No data is ever sent to any server. No analytics. No telemetry.

---

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release AAB (requires keystore.properties)
./gradlew bundleRelease

# Run tests
./gradlew testDebugUnitTest
```

---

## License

Proprietary. All rights reserved.

---

<p align="center">
  <b>Privora</b> — your phone, your data, your rules.
</p>
