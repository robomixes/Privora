# Privora — Project Status

Last updated: 2026-04-27

---

## Current Version

**Branch**: `v5-dev-gemma4`
**Latest commit**: `0f4340d` — Fix OOM when restoring backup over a populated vault
**Uncommitted**: Change PIN + AppPinManager (PBKDF2 hardening), MARKETING.md monetization strategy

---

## Feature Status

### Core Features (v1.0 — all DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Camera (photo + video) | DONE | `ui/camera/CaptureScreen.kt` |
| Object Detection (YOLOv8n) | DONE | `ui/camera/CameraScreen.kt` |
| Photo Editor (crop, rotate, filters) | DONE | `ui/camera/PhotoEditorScreen.kt` |
| Encrypted Vault | DONE | `security/VaultRepository.kt`, `ui/vault/VaultScreen.kt` |
| Secure Notes | DONE | `security/NoteRepository.kt`, `ui/notes/` |
| Document Scanner + OCR | DONE | `ui/scanner/ScannerScreen.kt` |
| QR Scanner + Generator | DONE | `ui/qrscanner/QrScannerScreen.kt` |
| Offline Translation (15 languages) | DONE | `ui/translate/TranslateScreen.kt` |
| Tools (Unit Converter) | DONE | `ui/tools/UnitConverterScreen.kt` |
| Backup / Restore (.paicbackup) | DONE | `security/BackupManager.kt` |
| Onboarding + Auth Mode | DONE | `ui/onboarding/OnboardingScreen.kt` |
| Settings (features, security, storage) | DONE | `ui/settings/SettingsScreen.kt` |

### Encryption & Security (v1.0 — all DONE)

| Feature | Status | Notes |
|---------|--------|-------|
| AES-256-GCM (KEK/DEK two-layer) | DONE | TEE/StrongBox backed |
| Biometric + App PIN auth | DONE | Rate limiting with escalating cooldowns |
| Emergency PIN (empty / wipe modes) | DONE | `security/DuressManager.kt` |
| EXIF stripping + face blur on share | DONE | Applied to all shared media |
| Screenshot protection (FLAG_SECURE) | DONE | Toggle in Settings |
| Auto-lock with configurable grace | DONE | `security/VaultLockManager.kt` |

### People / Contacts (v1.1 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Encrypted contacts (CRUD) | DONE | `security/ContactRepository.kt` |
| Face identity linking | DONE | `security/PrivoraDatabase.kt` (SQLCipher) |
| Self contact (system profile) | DONE | `ContactRepository.SELF_CONTACT_ID = "self"` |
| Health profile linking | DONE | Cross-links to Insights |
| Note + vault cross-references | DONE | Person filtering in notes + vault search |

### Gemma 4 On-Device AI (v1.2 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| LiteRT-LM engine (GPU-first, CPU fallback) | DONE | `bridge/GemmaRunner.kt` |
| Model download service (2.7GB) | DONE | `service/GemmaDownloadService.kt` |
| Notes AI (summarize, rewrite, extract, continue, grammar) | DONE | `bridge/GemmaPrompts.kt`, `ui/notes/NoteEditorScreen.kt` |
| Vault photo description (text-only, vision disabled) | DONE | Vision blocked by LiteRT-LM 0.10.0 bug |
| Daily AI tips on home landing | DONE | `ui/home/HomeLandingData.kt` |

### Insights Redesign (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Profile unification (shared chip row) | DONE | `ui/insights/ProfileFilter.kt` |
| Expenses (always self, no profile filter) | DONE | `ui/insights/ExpensesTab.kt` |
| Health (family profiles, charts, export) | DONE | `ui/insights/HealthTab.kt` |
| Medications (CRUD + linked reminders) | DONE | `ui/insights/MedicationsTab.kt` |
| Habits (streaks, calendar, export) | DONE | `ui/insights/HabitsTab.kt` |

### Reminders — Standalone Feature (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| One-time + recurring reminders | DONE | `ui/insights/ScheduleTab.kt`, `ui/reminders/RemindersScreen.kt` |
| AlarmManager with exact alarms | DONE | `service/ReminderScheduler.kt` |
| Done / Skip / Missed tracking | DONE | `service/ReminderActionReceiver.kt`, `service/MissedSweepWorker.kt` |
| Boot persistence | DONE | `service/BootReceiver.kt` |
| Med/Habit → Reminder linking | DONE | `ui/reminders/ReminderLinker.kt`, `ui/reminders/RemindersEditor.kt` |
| Habit auto-tick on Done | DONE | Propagates to HabitLog |
| Notification privacy (VISIBILITY_SECRET) | DONE | `service/ReminderReceiver.kt` |

### Home Layout (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Grid layout (default) | DONE | `ui/home/HomeScreen.kt` |
| Tabs layout (persistent bottom bar) | DONE | `ui/home/HomeTabsLayout.kt`, `ui/home/PrivoraBottomTabs.kt` |
| Tab bar hides with keyboard | DONE | `ui/PrivateAICameraApp.kt` |
| Lock/unlock in both top bars (PIN + biometric) | DONE | `ui/home/HomeScreen.kt`, `HomeTabsLayout.kt` |
| Today's reminders in Recent Activity | DONE | `ui/home/HomeTabsLayout.kt` |
| Tips above Recent Activity | DONE | Reordered in Tabs layout |
| Home redesign (gradient tip, mini-cards, photo stack, bold greeting) | DONE | `ui/home/HomeTabsLayout.kt` |

### AI Assistant (v1.4 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Chat surface with ✨ entry in top bar | DONE | `ui/assistant/AssistantScreen.kt` |
| Knowledge snapshot (8KB cap) | DONE | `bridge/KnowledgeSnapshot.kt` |
| 3 tools: search_notes, fetch_note, summarize_expenses | DONE | `bridge/AssistantTools.kt` |
| Markdown rendering (bold, italic, bullets, headers) | DONE | `ui/assistant/ChatBubble.kt` |
| Dynamic temperature (data vs creative) | DONE | 0.3 data / 0.7 creative |
| Persistent session (in-memory) | DONE | Singleton `AssistantSession` |
| Tappable data refs (single-topic: notes, reminders, habits, health) | DONE | `DataRef` + `RefKind` |
| Deep-link to specific notes | DONE | `notes?openNoteId=` route |
| New chat button | DONE | Top bar `+` icon |
| JSON cleanup (malformed model output) | DONE | `ParsedReply.cleanupText()` |
| Refined prompt (few-shot, personality, markdown guidance) | DONE | `bridge/AssistantPrompts.kt` |
| Deeper history (16 messages / 8 exchanges) | DONE | `bridge/AssistantPrompts.kt` |

### Market Readiness Fixes (v1.5 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Notification privacy (VISIBILITY_SECRET) | DONE | `service/ReminderReceiver.kt` |
| Share-to-Privora (ACTION_SEND) | DONE | `AndroidManifest.xml`, `MainActivity.kt`, `VaultScreen.kt`, `NotesScreen.kt` |
| Break-in / intruder alerts (Camera2) | DONE | `security/IntruderCapture.kt` |
| Calculator disguise (PIN+= unlock, duress) | DONE | `ui/disguise/CalculatorScreen.kt`, `CalculatorActivity.kt`, `DisguiseManager.kt` |

### Translation Enhancements (v1.6 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| 8s timeout on ML Kit translation | DONE | `ui/translate/TranslateScreen.kt` |
| AI grammar fix button (Gemma) | DONE | `ui/translate/TranslateScreen.kt` |
| Side-by-side Translate + AI Translate buttons | DONE | ML Kit for words, Gemma for sentences |
| Alternative translations with context notes | DONE | `getAlternativeTranslations()` |
| Keyboard dismiss on translate | DONE | `focusManager.clearFocus()` |
| Stale alternatives clear on re-translate | DONE | Tracked by `lastAltSource` |

### Vault Enhancements (v1.6 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Hidden folder (tap title N times to reveal) | DONE | `ui/vault/VaultScreen.kt` |
| Hidden folder config (tap count 3-15) | DONE | `ui/settings/SettingsScreen.kt` |
| Hidden folder suppressed during duress (no toasts) | DONE | `isDuressActive` guard |
| Move to Hidden from gallery/folder | DONE | Move dialog red "Hidden folder" option |
| Hidden folder viewer back → categories (not gallery) | DONE | `viewerFromHidden` flag |
| Vault UI polish (shadows, pill search, icon circles) | DONE | `CompactCategoryCard`, search bar |
| Smart filter icons (Duplicates, Blurry) | DONE | Leading icons on FilterChips |
| "+ New Folder" pill button | DONE | TextButton replaces lone + icon |
| Files as universal explorer (ALL vault items) | DONE | Loads all categories + folders |
| File type filter chips (All/Photos/Videos/PDF/Other) | DONE | `filesFilter` state in gallery |
| FILE media type for documents | DONE | `.file.enc` extension, `VaultMediaType.FILE` |
| PDF detection in `.file.enc` (Wi-Fi transferred PDFs) | DONE | Checks filename for `.pdf` |
| File display (icon + name + size + type badge) | DONE | PDF + FILE rendering in gallery + folder view |
| Folder multi-select (long-press → move/share/delete) | DONE | `combinedClickable` + selection action bar |
| Folder viewer back → folder (not gallery) | DONE | `viewerFromFolder` flag |
| Pinch-to-zoom image viewer (1x-5x) | DONE | `detectTransformGestures` + `graphicsLayer` |
| Double-tap zoom toggle (1x ↔ 2.5x) | DONE | `detectTapGestures(onDoubleTap)` |
| Pan when zoomed + swipe when not zoomed | DONE | Gesture mode switches on scale |
| Categories page scrollable (folders + trash reachable) | DONE | `verticalScroll` on normal view only |
| Duress leak fix: Photos/Videos/Files virtual views | DONE | Redirect to `openCategory()` in duress |
| Duress leak fix: folder counts show 0 | DONE | `if (isDuressActive) 0` |
| Translations: Photos, Videos, Files in AR/FR/ES/ZH | DONE | All 4 locales |
| All Wi-Fi received files → Received folder | DONE | Images, videos, PDFs, docs — all in one folder |

### Wi-Fi Transfer (v1.6 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| NanoHTTPD embedded server | DONE | `service/WifiTransferServer.kt` |
| QR code + URL + PIN display | DONE | `ui/vault/WifiTransferScreen.kt` |
| Browser-side AES-256-CTR encryption (pure JS) | DONE | No crypto.subtle needed, works on HTTP |
| Server-side decryption with PIN-derived key | DONE | `decryptWithPin()` |
| PIN never sent over network | DONE | Both sides derive key from PIN+salt |
| All files → Received folder | DONE | Images, videos, PDFs, docs unified |
| Configurable size limit (50MB-1GB) | DONE | Advanced Settings |
| 3 wrong PINs = server lockout | DONE | `MAX_PIN_FAILURES = 3` |
| Wi-Fi IP detection (wlan0 + fallback) | DONE | `getLocalIpAddress()` |
| 📶 icon in vault top bar | DONE | Hidden during duress |
| Fallback: multipart + PIN when encryption fails | DONE | Dual-mode upload handler |

### Security & Stability (v1.8 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Change PIN flow (current → new → confirm) | DONE | `ui/settings/ChangePinScreen.kt` |
| App PIN hardened with PBKDF2 (10K iter, 256-bit, salted) | DONE | `security/AppPinManager.kt` |
| Lazy migration: legacy plaintext PINs re-hashed on first unlock | DONE | `AppPinManager.verify()` |
| Duress-collision guard (new PIN can't equal duress PIN) | DONE | `ChangePinScreen.kt` |
| Calculator screen takes verifier lambda (no raw PIN in memory) | DONE | `CalculatorScreen.kt` |
| Calculator disguise crash on PIN + = unlock — FIXED | DONE | `LauncherDefault` alias keeps MainActivity enabled |
| Backup OOM on 380MB+ restore over populated vault — FIXED | DONE | Streaming re-encrypt, one plaintext at a time |
| 5-locale translations for Change PIN (10 keys × 5) | DONE | EN/AR/FR/ES/ZH |

### Password Hints (v1.7 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Encrypted hint vault (new top-level feature) | DONE | `security/PasswordHintRepository.kt` |
| 8 color-coded categories (Email, Social, Banking, etc.) | DONE | `HintCategory` enum |
| Favorites / starred entries pinned to top | DONE | `isFavorite` field |
| Search across service name, hints, notes | DONE | `PasswordHintRepository.search()` |
| Category filter chips | DONE | Horizontal scroll row |
| Add/Edit dialog with all fields | DONE | `ui/passwords/PasswordHintDialog.kt` |
| Password generator (SecureRandom, length slider, options) | DONE | `ui/passwords/PasswordGenerator.kt` |
| Copy with 30s auto-clear clipboard | DONE | `copyWithAutoClear()` |
| Auth gate (PIN / biometric) | DONE | Same pattern as Vault/Notes |
| Duress mode: empty list | DONE | `isDuressActive` guard |
| Home tile (🔑 purple) | DONE | `ui/home/HomeScreen.kt` |
| Settings feature toggle | DONE | `FeatureToggleManager` + `SettingsScreen` |
| Included in backup/restore automatically | DONE | `.hint.enc` files in `vault/passwords/` |
| 5-locale translations (22 keys) | DONE | EN/AR/FR/ES/ZH |

---

## Known Issues / Limitations

| Issue | Impact | Status |
|-------|--------|--------|
| Gemma 4 vision crashes (LiteRT-LM 0.10.0) | Photo Q&A disabled | Waiting for 0.10.1 |
| Intruder capture device-dependent | Camera2 headless may fail on some devices | Plain JPEG fallback |
| Duress mode crashes on Insights/Notes entry | Concurrent file deletion during navigation | Needs null-safety guards |
| AI streaming not implemented | Full response after 3-10s wait | Architecture ready |
| Wi-Fi transfer key derivation is custom mixing | Not PBKDF2, uses 10K-round iterative mix | Acceptable for ephemeral transfers |
| PDFs open in external reader (Google Drive/Files) | Decrypted PDF touches external app briefly | Built-in PDF viewer would be better |

---

## What's Next

### Not Started

| Feature | Priority | Effort |
|---------|----------|--------|
| Fix duress mode crashes (Insights/Notes) | High | 1 day |
| AI streaming responses (token-by-token) | Medium | 1 day |
| AI voice input (on-device SpeechRecognizer) | Low | 2 days |
| AI action proposals (create reminder from chat) | Medium | 2-3 days |
| Gemma vision (photo Q&A) | Blocked | Waiting for LiteRT-LM 0.10.1 |
| Home screen widgets | Low | 1-2 days |
| Audio transcription in notes | Medium | 2 days |
| Cross-device sync (encrypted cloud) | High | 2-3 weeks |

### Play Store Readiness

| Item | Status |
|------|--------|
| App name: Privora | DONE |
| Version management | DONE |
| Privacy policy (PRIVACY.md) | DONE |
| Adaptive icon | DONE |
| ProGuard/R8 | DONE |
| Signing config | DONE |
| Data Safety declaration | TODO (manual in Play Console) |
| Screenshots + feature graphic | TODO (manual) |
| Store listing description | TODO (manual) |

---

## Architecture

```
Privora (Kotlin + Jetpack Compose + Material 3)
├── UI Layer
│   ├── Camera (CaptureScreen, CameraScreen, PhotoEditor)
│   ├── Vault (VaultScreen, folders, hidden folder, viewer, video player, Wi-Fi transfer)
│   ├── Notes (NotesScreen, NoteEditorScreen, AI sheet)
│   ├── Scanner (ScannerScreen + OCR + AI receipt extraction)
│   ├── QR (QrScannerScreen + generator)
│   ├── Translate (TranslateScreen + AI translate + grammar fix + alternatives)
│   ├── Contacts (ContactsScreen)
│   ├── Insights (Expenses, Health + AI summary, Meds, Habits)
│   ├── Reminders (RemindersScreen, RemindersEditor, ReminderLinker)
│   ├── Passwords (PasswordHintsScreen, PasswordHintDialog, PasswordGenerator)
│   ├── Assistant (AssistantScreen, ChatBubble + markdown, data refs)
│   ├── Disguise (CalculatorScreen, CalculatorActivity, DisguiseManager)
│   ├── Home (Grid + Tabs layouts, gradient tips, mini-card activity, photo stack)
│   ├── Settings (features, security, AI, disguise, intruder alerts, hidden folder, transfer limit)
│   └── Onboarding (welcome, auth, PIN, benchmark)
├── Bridge Layer (AI)
│   ├── GemmaRunner (LiteRT-LM engine, GPU/CPU, mutex, completeJson)
│   ├── GemmaPrompts (notes AI + FIX_GRAMMAR)
│   ├── AssistantPrompts (chat system instruction, few-shot, markdown)
│   ├── AssistantTools (search_notes, fetch_note, summarize_expenses, ParsedReply)
│   ├── KnowledgeSnapshot (compact data builder, 8KB cap)
│   └── GemmaModelManager (download service)
├── Security Layer
│   ├── CryptoManager (AES-256-GCM, KEK/DEK)
│   ├── VaultRepository (photos, videos, PDFs, files with .file.enc)
│   ├── NoteRepository (encrypted notes)
│   ├── InsightsRepository (expenses/health/habits/meds/schedule)
│   ├── PasswordHintRepository (encrypted password hints)
│   ├── ContactRepository (encrypted contacts, self profile)
│   ├── PrivoraDatabase (SQLCipher)
│   ├── VaultLockManager (grace period, duress state)
│   ├── DuressManager (emergency PIN, wipe)
│   ├── AppPinManager (PBKDF2-hashed app PIN, lazy migration from legacy plaintext)
│   ├── PinRateLimiter (escalating cooldowns + intruder trigger)
│   ├── IntruderCapture (Camera2 front capture on wrong PIN)
│   ├── BackupManager (export/import .paicbackup)
│   └── FolderManager (custom vault folders)
├── Service Layer
│   ├── WifiTransferServer (NanoHTTPD + pure-JS AES-256-CTR encryption)
│   ├── ReminderScheduler (AlarmManager wrapper)
│   ├── ReminderReceiver (notification with Done/Skip + VISIBILITY_SECRET)
│   ├── ReminderActionReceiver (mark done/skip + habit propagation)
│   ├── BootReceiver (re-register alarms)
│   ├── MissedSweepWorker (daily WorkManager)
│   ├── GemmaDownloadService (foreground model download)
│   └── CrashHandler (local logging)
└── Dependencies
    ├── CameraX 1.4.2
    ├── LiteRT-LM 0.10.0 (Gemma 4)
    ├── ONNX Runtime 1.21.0 (YOLOv8n)
    ├── NanoHTTPD 2.3.1 (Wi-Fi transfer)
    ├── ML Kit (scanner, OCR, barcode, translate, face detection)
    ├── SQLCipher (encrypted database)
    ├── WorkManager 2.9.1
    ├── AndroidX Biometric 1.1.0
    ├── Navigation Compose 2.9.0
    └── Kotlin 2.3.20, AGP 8.9.3, minSdk 26, targetSdk 35
```

## Commit History (Recent)

| Commit | Date | Description |
|--------|------|-------------|
| `0f4340d` | 2026-04-24 | Fix OOM when restoring backup over a populated vault (streaming re-encrypt) |
| `ccb1d5a` | 2026-04-24 | Fix calculator disguise crash on PIN + = (LauncherDefault alias, MainActivity stays enabled) |
| `12b4352` | 2026-04-24 | Password Hints feature + backup streaming fix + contacts in backup |
| `4e4913a` | 2026-04-23 | Duress fixes, PDF detection, folder multi-select, pinch-to-zoom, all-to-Received |
| `735437c` | 2026-04-23 | Vault: Files as universal explorer with filters, translations, scroll + nav fixes |
| `3b819f1` | 2026-04-22 | Wi-Fi Transfer with browser-side encryption, Files category, vault fixes |
| `c5d9e91` | 2026-04-22 | Translate AI + grammar + alternatives, vault hidden folder, home redesign |
| `15d65b7` | 2026-04-20 | Update README, PLAN, and add STATUS.md |
| `bb519ce` | 2026-04-16 | Calculator disguise + Share-to-Privora + duress from calculator |
| `28cc0f0` | 2026-04-16 | AI Assistant + intruder alerts + notification privacy + home UX polish |
| `59c6f52` | 2026-04-15 | Insights redesign + standalone Reminders feature |
| `5db4a9f` | 2026-04-14 | Home layout: Grid/Tabs option + dynamic landing with AI tips |
| `38fcaa1` | 2026-04-13 | GPU backend with CPU fallback, OpenCL manifest fix |
| `42b14cf` | 2026-04-13 | Phase 3: Photo Intelligence DB + Vision (disabled) |
| `1dc8c36` | 2026-04-12 | Gemma 4 Phase 1: Notes Intelligence with on-device LLM |
