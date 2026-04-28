# Privora

**Private AI Camera, Encrypted Vault & Personal Assistant**

Privora is a privacy-first Android app that combines an AI-powered camera, encrypted vault, secure notes, personal insights, scheduled reminders, and an on-device AI assistant — all running entirely on your device. No cloud. No analytics. No telemetry.

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-blue.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](#license)

---

## Features

### Camera & Detection
- Photo and video capture with real-time YOLOv8n object detection overlay
- Hold-to-record quick video, timer, front/back toggle
- Photo editor with crop, rotate, filters, text, and stickers
- Face detection + face grouping with person identity linking
- EXIF stripping + face blur on share

### Encrypted Vault
- AES-256-GCM encryption with hardware-backed keys (TEE/StrongBox)
- 6 categories: Camera, Videos, Scans, Detections, Reports, Files
- Custom folders and subfolders with drag-and-drop organization
- Full-text search across all vault items
- Built-in video player with seek and tap-to-pause
- Bulk import from gallery (multi-select picker)
- **Share-to-Privora**: receive images, videos, text, and PDFs from any app via the share sheet

### Secure Notes
- Encrypted note-taking with 9 color themes
- Markdown formatting: bold, italic, strikethrough, underline, checklists
- Audio recording with encrypted storage
- Photo attachments from vault
- Person-linking to encrypted contacts
- Tags, search, pin, multi-select, bulk operations
- **AI-powered**: summarize, rewrite, extract tasks, fix grammar, continue writing (Gemma 4)

### Document Scanner + OCR
- Multi-page scanning (up to 10 pages) via ML Kit
- Enhancement modes: Auto, B&W, Color
- OCR text extraction with copy-to-clipboard
- Save as encrypted PDF (A4, 150 DPI)

### QR & Barcode Scanner
- Live scanning with 50-item history
- QR code generator
- Context-aware actions (URL, phone, email, SMS, Wi-Fi)

### Translation
- Offline translation between 15 languages
- Camera and gallery input with text overlay
- Text-to-speech support with bidirectional swap

### People / Encrypted Contacts
- Encrypted address book with phone, email, links
- Face identity linking from vault photos
- Health profile linking for per-person insights
- Notes and vault cross-references

### Personal Insights (Encrypted)
- **Expenses** — category tracking, pie charts, month navigation, PDF/CSV export
- **Health** — family profiles, weight, HR, BP, sleep, mood, temperature, steps, trend charts, AI weekly summary
- **Medications** — name, dosage, instructions, linked reminders with alarms
- **Habits** — daily checklist, streak counter, calendar heatmap, PDF export

### Reminders (Standalone Feature)
- One-time and recurring reminders with AlarmManager
- Done / Skip / Missed tracking with daily sweep
- Boot persistence (re-registers alarms on reboot)
- Linked to medications and habits (Done propagates to habit checklist)
- **Notification privacy**: content hidden on lock screen (VISIBILITY_SECRET)

### AI Assistant (On-Device)
- **Gemma 4 E2B** (2.7GB) running locally via LiteRT-LM — zero cloud
- ✨ entry point in the home top bar
- Chat surface with markdown rendering (bold, italic, bullets, section headers)
- Knowledge snapshot: reads reminders, expenses, notes (titles only), habits, health, medications
- 3 tools: `search_notes`, `fetch_note`, `summarize_expenses`
- Dynamic temperature (0.3 for data queries, 0.7 for creative tasks)
- Persistent chat session (clears on app kill — no disk write)
- Tappable data references (notes → opens editor, reminders → Reminders screen, habits/health → Insights)
- Deep-link into specific notes from search results

### Home Screen
- **Grid layout**: feature tiles, colored icons
- **Tabs layout**: persistent bottom tab bar, greeting, daily AI tips, recent activity with today's reminders
- Layout toggle in Settings
- Lock/unlock from both layouts (PIN + biometric)

### Security
| Feature | Details |
|---------|---------|
| Encryption | AES-256-GCM with 12-byte IV and 128-bit tag |
| Key Storage | Two-layer: KEK in Android Keystore (TEE/StrongBox) wraps DEK |
| Authentication | Biometric (fingerprint/face) or app PIN |
| Emergency PIN | Empty-vault mode (reusable) or full wipe mode |
| Calculator Disguise | App appears as "Calculator" in launcher; PIN+= unlocks Privora; duress PIN+= triggers wipe |
| Intruder Alerts | Silent front-camera capture on wrong PIN (Camera2 API), encrypted, auto-deletes after 30 days |
| EXIF Stripping | GPS, device info, timestamps removed from all shared media |
| Face Blur | Automatic face detection and pixelation before sharing |
| Screenshot Protection | FLAG_SECURE blocks screenshots and screen recording |
| Auto-lock | Configurable grace period (immediate to 2 minutes) |
| Notification Privacy | Reminder content hidden on phone lock screen |
| PIN Rate Limiting | Escalating cooldowns: 30s → 2min → 5min → 15min |
| Backup | Encrypted .paicbackup with PBKDF2-SHA256 (600K iterations) |

---

## Tech Stack

- **Language**: Kotlin 2.3.20
- **UI**: Jetpack Compose + Material Design 3
- **Camera**: CameraX 1.4.2 (Preview + VideoCapture + ImageAnalysis)
- **On-Device AI**: Gemma 4 E2B via LiteRT-LM 0.10.0 (GPU-first, CPU fallback)
- **Object Detection**: ONNX Runtime (YOLOv8n, 640x640, fully offline)
- **ML Kit**: Document scanner, OCR, barcode scanning, translation, face detection
- **Database**: SQLCipher (encrypted contacts, photo index)
- **Scheduler**: AlarmManager + WorkManager (reminders, missed sweep)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)

---

## Localization

| Language | Status |
|----------|--------|
| English | Complete |
| Arabic (RTL) | Complete |
| Spanish | Complete |
| French | Complete |
| Chinese | Complete |

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

# Install Play Store flavor on a connected device (default)
./gradlew installPlaystoreDebug

# Or build the F-Droid flavor (no IAP code, pure OSS)
./gradlew installFdroidDebug
```

---

## Roadmap

Privora ships on Android today. The on-device-AI architecture, encryption stack, and feature set are designed to port to other platforms:

- **iOS** — planned
- **Windows** — planned
- **Linux** — planned

The roadmap order will follow user demand. The free Android build will always remain the reference implementation.

---

## Contributing

Contributions are welcome. Before opening a pull request:

1. Read [CONTRIBUTING.md](CONTRIBUTING.md) for build setup and style.
2. Sign the [Contributor License Agreement](CLA.md) — first-time contributors will be prompted automatically by [CLA Assistant](https://cla-assistant.io/) on their first PR. The CLA lets the project owner offer a commercial license to embedders who want to ship without AGPL obligations (the same pattern used by Bitwarden, Sentry, GitLab CE).

Security issues should be reported privately — see [SECURITY.md](SECURITY.md).

---

## License

Privora is licensed under the **GNU Affero General Public License v3.0 or later** ([AGPL-3.0-or-later](LICENSE)). You can use, study, modify, and redistribute the source under those terms.

A separate **commercial license** is available from the copyright holder for organisations that want to embed Privora in a closed-source product without the AGPL's network-source-distribution obligations. Contact the maintainer for terms.

This dual-licensing approach is what allows future Pro features to ship as a closed module while the core stays open and auditable for everyone.

See [THREAT_MODEL.md](THREAT_MODEL.md) for the security posture and what Privora does (and doesn't) protect against.

---

<p align="center">
  <b>Privora</b> — your phone, your data, your rules.
</p>
