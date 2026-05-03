# Changelog

All notable changes to Privora are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) loosely; entries
focus on what users perceive plus what's relevant to auditors.

Privora is licensed under [AGPL-3.0-or-later](LICENSE).

---

## [2.0.5] — 2026-05-02

### Fixed
- Android 14-and-earlier crashes triggered by Kotlin's `MutableList.removeLast()`
  shadowing Java's new `List.removeLast()` introduced in Android 15. All call
  sites in the QR scanner now use `removeAt(list.lastIndex)` per Google's
  guidance.

## [2.0.4] — 2026-05-02

### Added
- Stalled-download detection: the AI model download is now flagged as failed
  if no bytes arrive for 60 seconds, with an in-app Retry button.
- DownloadManager can fall back to cellular if Wi-Fi drops
  (`setAllowedOverMetered(true)`).

### Fixed
- Stuck downloads no longer hang silently. The user gets a clear error and
  can recover without uninstalling.

## [2.0.3] — 2026-05-02

### Fixed
- AI model download lifecycle: disable + re-enable AI now properly cancels
  the prior in-flight download and starts a fresh one. Previously a stalled
  download couldn't be recovered without clearing app data.
- "Delete model" in Settings now also cancels any in-flight download.

## [2.0.2] — 2026-05-02

### Changed
- AI model download now uses Android's system `DownloadManager` instead of a
  custom foreground service. Drops the `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`, and `WAKE_LOCK` permissions from the
  manifest. The user-visible behaviour is the same but the app no longer
  needs Play Console approval for foreground-service usage.
- Model file relocated from internal `filesDir/models/` to external
  app-private storage (`getExternalFilesDir("models")`) — required by
  DownloadManager. One-time migration on first launch moves any existing
  file in place; users do not redownload.

### Removed
- `USE_EXACT_ALARM` permission. Reminders fall back to the user-grantable
  `SCHEDULE_EXACT_ALARM`. Required by Play policy: `USE_EXACT_ALARM` is
  reserved for apps whose core functionality is alarm clock or calendar.

## [2.0.1] — 2026-05-02

### Fixed
- Native library 16-KB page-size alignment for Android 15. Bumped ONNX
  Runtime 1.21.0 → 1.22.0 to pick up the aligned `libonnxruntime4j_jni.so`.
  All `arm64-v8a` and `x86_64` libraries now report `0x4000` segment
  alignment, satisfying Play Console's compliance check.

## [2.0.0] — 2026-04-29 — *Public source release*

### Added
- **Repository now public** under [AGPL-3.0-or-later](LICENSE).
- Contributor License Agreement (`CLA.md`, Harmony HA-CLA-I-Lite template),
  enforced via [CLA Assistant](https://cla-assistant.io/) on every external
  pull request.
- `SECURITY.md` with vulnerability-reporting policy.
- `THREAT_MODEL.md` describing exactly what Privora protects against and
  what it does not.
- `CONTRIBUTING.md` with build instructions for both `fdroid` and
  `playstore` Gradle flavors.
- SPDX headers on every Kotlin source file.
- `fdroid` / `playstore` Gradle product flavors, scaffolded so a closed
  Pro module can ship through Play Store later without contaminating the
  F-Droid build.

### Changed
- App rebranded from *Private AI Camera* → *Privora*.
- Theme picker (System / Light / Dark) in Settings.
- Home grid + bottom tabs now adapt cleanly to dark mode (per-feature
  accent colour computed at render time).
- AI Assistant chat: streaming token-by-token responses; one-tap action
  proposals for reminders, expenses, notes, health records, contacts,
  medications, and habits.
- Vault: built-in PDF viewer (decrypted PDFs no longer leave the app),
  hidden folders, pinch-to-zoom image viewer, multi-select in folders.
- Wi-Fi transfer with browser-side AES-256-CTR encryption (encrypts before
  bytes leave the source device, even on plain HTTP).
- Calculator disguise + duress PIN.
- Password hint manager with secure generator and 30-second auto-clear
  clipboard.
- Translation: 8-second timeout on ML Kit, AI grammar fix, alternative
  phrasings.
- Backup / restore now includes the SQLCipher contacts database. OOM
  during large-vault re-encryption fixed by streaming one file at a time.
- App PIN storage hardened: PBKDF2-SHA256, 10 000 iterations, 256-bit
  salt. Lazy migration from any pre-2.0 plaintext PIN on first unlock.
- New "Change PIN" flow in Advanced Settings.
- Home-screen widgets: Quick Note, Reminders, AI Assistant, Quick Access XL.

### Security
- App PIN: PBKDF2-hashed, salted (was plaintext in 1.x).
- Vault data: AES-256-GCM with hardware-backed KEK in Android Keystore.
- Contacts + photo index: SQLCipher.
- No analytics, no telemetry, no third-party SDKs in privacy-sensitive
  paths. See `THREAT_MODEL.md`.

---

## [1.0.0] — 2026-03 — *Initial Play Store release as "Private AI Camera"*

Pre-rebrand. See git history for details.
