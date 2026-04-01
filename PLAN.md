# Private AI Camera — Project Plan

**Core value proposition**: "AI camera that never sends your data anywhere."

**Architecture**: Native Android (Kotlin) + Jetpack Compose + CameraX + ONNX Runtime + ML Kit + AES-256-GCM encryption

**GitHub**: https://github.com/robomixes/private-ai-camera

---

## v1.0 Release — Feature Complete

### Core Features

| # | Feature | Status |
|---|---------|--------|
| 1 | **Object Detection** — YOLOv8n, tap-to-focus, freeze frame + blur, web search by image, front/back camera | DONE |
| 2 | **Photo Capture** — encrypted to vault, shutter animation, thumbnail preview, direct viewer from camera | DONE |
| 3 | **Video Recording** — PHOTO/VIDEO toggle, hold-to-record from photo mode, timer, 150MB/10min cap, encrypted vault | DONE |
| 4 | **Document Scanner + OCR** — ML Kit multi-page (up to 10), enhance modes (Auto/B&W/Color), OCR copy, PDF share (A4 150DPI) | DONE |
| 5 | **QR/Barcode Scanner** — live scan, vibration, flash toggle, 50-item history, context-aware actions (URL/Phone/Email/etc.) | DONE |
| 6 | **Local Translation** — 15 languages, camera + gallery input, Google Lens-style image overlay, TTS, language swap | DONE |

### Encrypted Vault

| # | Feature | Status |
|---|---------|--------|
| 7 | **Vault** — AES-256-GCM, Android Keystore TEE/StrongBox, two-layer keys (KEK wraps DEK), biometric + PIN auth | DONE |
| 8 | **Categories** — Camera Photos, Videos, Scanned Documents, date-grouped gallery | DONE |
| 9 | **Multi-select** — long-press, share images/PDF/video, save to device, delete | DONE |
| 10 | **Video Player** — seek bar, current/total time, tap-to-pause, play/pause button | DONE |

### Secure Notes

| # | Feature | Status |
|---|---------|--------|
| 11 | **Notes** — Keep-style staggered grid, 9 colors, pin/unpin, tags, search, encrypted (JSON -> AES-GCM) | DONE |

### Privacy & Security

| # | Feature | Status |
|---|---------|--------|
| 12 | **EXIF Stripping** — GPS, device info, timestamps removed from all shared images | DONE |
| 13 | **Face Blur on Share** — ML Kit face detection + pixelation, global toggle + per-share flip button | DONE |
| 14 | **Screenshot Block** — FLAG_SECURE on all activities | DONE |
| 15 | **Auto-lock** — grace period (0/10/30/60/120s), shared between vault and notes, manual lock button | DONE |
| 16 | **Emergency PIN** — empty-only mode (reusable) + wipe mode (delete key → fresh key → background file cleanup) | DONE |
| 17 | **Auth Mode Choice** — onboarding lets user pick phone lock vs app PIN, duress only with app PIN | DONE |

### Data Management

| # | Feature | Status |
|---|---------|--------|
| 18 | **Backup & Migration** — key export approach, single .paicbackup file (DEK wrapped + .enc files zipped as-is) | DONE |
| 19 | **Import with re-encryption** — existing data re-encrypted with imported DEK, nothing lost | DONE |
| 20 | **Import from onboarding** — "I have a backup" on welcome screen, completes auth setup after import | DONE |

### App Experience

| # | Feature | Status |
|---|---------|--------|
| 21 | **Home Screen** — feature grid, colored lock/settings icons, import success banner | DONE |
| 22 | **Onboarding** — Welcome → Privacy → Auth Mode → PIN Setup → All Set (+ device benchmark) | DONE |
| 23 | **Settings** — Features, Device, Security (lock delay, emergency PIN), Storage, Privacy, Backup, About | DONE |
| 24 | **Feature Toggles** — enable/disable any feature from home screen via settings | DONE |
| 25 | **Camera Thumbnail** — tap opens photo/video directly with share + delete (no vault navigation) | DONE |

### v1.0 Release Prep (TODO)

| Item | Effort |
|------|--------|
| Privacy Policy (in-app link + web page) | Manual |
| Play Store Data Safety declaration (ML Kit disclosure) | Manual |
| Proper adaptive icon (launcher icon) | Small |
| Release build test (Proguard/R8 verification) | Small |
| Version bump 0.1.0 → 1.0.0 | Trivial |
| Release signing keystore | Small |
| Play Store screenshots + description | Manual |

---

## v1.1 — Photo Editing + Polish

| Feature | Description |
|---------|-------------|
| Photo Editing | Crop, rotate, text overlay, basic filters — applied in memory, re-encrypted on save |
| Note Attachments | Attach photos from vault to notes |
| App UI Localization | English, Arabic, Spanish, French, Chinese, RTL support |
| Crash Handler | Global uncaught exception handler to prevent data loss |
| Performance | Device profiler tier actually gates features (profiler runs but doesn't limit anything yet) |

## v2.0 — Extended AI

| Feature | Description |
|---------|-------------|
| Plant/Animal ID | EfficientNet-Lite via ONNX |
| On-device Photo Search | MobileNet embeddings + vector similarity |
| Scene Description | Accessibility feature — describe what camera sees |
| Color Identifier | Identify colors in frame |
| License Plate Reader | Port from Python ai-camera project |

## v3.0 — Remove Google Dependencies

| Feature | Description |
|---------|-------------|
| Custom Document Scanner | Replace ML Kit scanner with OpenCV C++ (live edge overlay, auto-capture) |
| Custom Translation | Replace ML Kit Translate with local ONNX models |
| Custom OCR | Replace ML Kit Text Recognition with local model |
| Zero Google Play Services | App works fully offline on any Android device |

## v4.0 — Multi-Platform

| Feature | Description |
|---------|-------------|
| Desktop App | Windows/macOS/Linux — Compose Multiplatform, webcam + file import, shared .paicbackup sync |
| iOS Port | Swift + SwiftUI, AVFoundation, Secure Enclave, same .enc file format |

---

## Architecture

```
Android App (Kotlin + Jetpack Compose)
├── UI Layer (Compose)
│   ├── Camera (CameraX preview + detection overlay + video recording)
│   ├── Vault (encrypted gallery, categories, viewer, video player)
│   ├── Scanner (ML Kit document scanner + OCR)
│   ├── QR Scanner (ML Kit barcode scanning)
│   ├── Translate (ML Kit translation + image overlay + TTS)
│   ├── Notes (encrypted editor, tags, colors, search)
│   ├── Home (feature grid + lock + settings)
│   ├── Onboarding (auth mode + PIN + biometric + benchmark)
│   └── Settings (features, security, storage, privacy, backup, about)
├── Security Layer
│   ├── CryptoManager (AES-256-GCM, two-layer KEK/DEK, auto-initialize)
│   ├── KeyManager (Android Keystore TEE/StrongBox)
│   ├── VaultRepository (encrypted photo/video storage)
│   ├── NoteRepository (encrypted note storage)
│   ├── VaultLockManager (shared grace period)
│   ├── DuressManager (emergency PIN, wipe with settings preservation)
│   ├── BackupManager (key export + zip)
│   └── BackupKeyDerivation (PBKDF2-SHA256 600K)
├── AI Layer
│   └── OnnxDetector (YOLOv8n via ONNX Runtime Java API)
├── Utilities
│   ├── ImageUtils (crop, EXIF strip, image search)
│   ├── FaceBlur (ML Kit face detection + pixelation)
│   ├── DeviceProfiler (benchmark, tier classification)
│   └── StorageManager (vault/notes/cache size tracking)
└── Dependencies
    ├── CameraX 1.4.2 (core, camera2, lifecycle, view, video)
    ├── ONNX Runtime 1.21.0
    ├── ML Kit (document-scanner, text-recognition, barcode, translate, face-detection)
    ├── AndroidX Biometric 1.1.0
    ├── Navigation Compose 2.9.0
    └── Kotlin 2.1.20, AGP 8.9.3, minSdk 26, targetSdk 35
```

## Network Policy

| Use | Policy |
|-----|--------|
| AI inference | **NEVER** — all on-device |
| User data | **NEVER** — all local |
| Web search | Opens **external browser** via intent |
| Models | YOLOv8n **bundled in APK**. Translation models via Google Play Services |
| Analytics | **NONE** — no Firebase, no Crashlytics, no telemetry |

## Encryption Design

```
Biometric / Device Credential / App PIN
    │
    ▼
KEK (Android Keystore TEE/StrongBox) ──wraps──▶ DEK (app-internal storage)
                                                    │
                                                    ▼
                                           AES-256-GCM encrypt/decrypt
                                           ├── Photos: {id}.enc + {id}.thumb.enc
                                           ├── Videos: {id}.vid.enc + {id}.vid.thumb.enc
                                           └── Notes: {id}.note.enc (JSON)

Backup: DEK wrapped with PBKDF2-derived key from user password
        .enc files zipped as-is (no re-encryption)
        Single .paicbackup file

Emergency Wipe: Delete KEK (instant, data unreadable) → fresh keys → rename files _tobedeleted_ → background cleanup
