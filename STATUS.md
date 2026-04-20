# Privora — Project Status

Last updated: 2026-04-20

---

## Current Version

**Branch**: `v5-dev-gemma4`
**Latest commit**: `bb519ce` — Calculator disguise + Share-to-Privora + duress from calculator

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
| One-time + recurring reminders | DONE | `ui/insights/ScheduleTab.kt` (body), `ui/reminders/RemindersScreen.kt` (host) |
| AlarmManager with exact alarms | DONE | `service/ReminderScheduler.kt` |
| Done / Skip / Missed tracking | DONE | `service/ReminderActionReceiver.kt`, `service/MissedSweepWorker.kt` |
| Boot persistence | DONE | `service/BootReceiver.kt` |
| Med/Habit → Reminder linking | DONE | `ui/reminders/ReminderLinker.kt`, `ui/reminders/RemindersEditor.kt` |
| Habit auto-tick on Done | DONE | `service/ReminderActionReceiver.kt` (propagates to HabitLog) |
| One-shot Done/Skip fix | DONE | Time-key normalization ONESHOT → HH:mm |
| Notification privacy | DONE | VISIBILITY_SECRET on lock screen |

### Home Layout (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Grid layout (default) | DONE | `ui/home/HomeScreen.kt` |
| Tabs layout (persistent bottom bar) | DONE | `ui/home/HomeTabsLayout.kt`, `ui/home/PrivoraBottomTabs.kt` |
| Tab bar hides with keyboard | DONE | `ui/PrivateAICameraApp.kt` (IME check) |
| Lock/unlock in both top bars | DONE | PIN dialog + biometric, Grid + Tabs |
| Today's reminders in Recent Activity | DONE | `ui/home/HomeTabsLayout.kt` |
| Daily AI tips above Recent Activity | DONE | Reordered in Tabs layout |

### AI Assistant (v1.4 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Chat surface with ✨ entry in top bar | DONE | `ui/assistant/AssistantScreen.kt` |
| Knowledge snapshot (8KB cap) | DONE | `bridge/KnowledgeSnapshot.kt` |
| 3 tools: search_notes, fetch_note, summarize_expenses | DONE | `bridge/AssistantTools.kt` |
| Markdown rendering (bold, italic, bullets, headers) | DONE | `ui/assistant/ChatBubble.kt` |
| Dynamic temperature (data vs creative) | DONE | 0.3 data / 0.7 creative |
| Persistent session (in-memory) | DONE | Singleton `AssistantSession` |
| Tappable data refs (notes, reminders, habits, health) | DONE | `DataRef` + `RefKind` |
| Deep-link to specific notes | DONE | `notes?openNoteId=` route |
| New chat button | DONE | Top bar `+` icon |
| JSON cleanup (malformed model output) | DONE | Multi-layer regex in `ParsedReply.cleanupText()` |
| completeJson() tolerant parser | DONE | `GemmaRunner.kt` |

### Market Readiness Fixes (v1.5 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Notification privacy (VISIBILITY_SECRET) | DONE | `service/ReminderReceiver.kt` |
| Share-to-Privora (ACTION_SEND) | DONE | `AndroidManifest.xml`, `MainActivity.kt`, `VaultScreen.kt`, `NotesScreen.kt` |
| Break-in / intruder alerts | DONE | `security/IntruderCapture.kt` (Camera2 API) |
| Calculator disguise | DONE | `ui/disguise/CalculatorScreen.kt`, `CalculatorActivity.kt`, `DisguiseManager.kt` |
| Duress from calculator | DONE | Duress PIN + = triggers vault wipe |

---

## Known Issues / Limitations

| Issue | Impact | Workaround |
|-------|--------|------------|
| Gemma 4 vision crashes (LiteRT-LM 0.10.0) | Photo Q&A disabled | Waiting for 0.10.1 Maven release |
| Intruder capture may fail on some devices | Camera2 headless capture is device-dependent | Photo saved as plain JPEG in app-private dir |
| Calculator disguise disables MainActivity component | Sharing/deep-links to Privora won't work while disguised | Toggle off disguise to use share-to-Privora |
| AI assistant doesn't stream responses | Full response appears after 3-10s wait | Architecture ready, not wired yet |

---

## What's Next

### Not Started

| Feature | Priority | Effort |
|---------|----------|--------|
| AI streaming responses (token-by-token) | Medium | 1 day |
| AI voice input (on-device SpeechRecognizer) | Low | 2 days |
| AI action proposals (create reminder, add expense from chat) | Medium | 2-3 days |
| Gemma vision (photo Q&A) | Blocked | Waiting for LiteRT-LM 0.10.1 |
| Home screen widgets (quick note, locked capture) | Low | 1-2 days |
| Video editor (trim, filter) | Low | 3-5 days |
| Health app integration (Google Fit) | Low | 2-3 days |
| Audio transcription in notes | Medium | 2 days |
| Cross-device sync (encrypted cloud) | High | 2-3 weeks |
| Desktop/iOS port | Future | Months |

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
│   ├── Vault (VaultScreen, folders, viewer, video player)
│   ├── Notes (NotesScreen, NoteEditorScreen, AI sheet)
│   ├── Scanner (ScannerScreen + OCR)
│   ├── QR (QrScannerScreen + generator)
│   ├── Translate (TranslateScreen + TTS)
│   ├── Contacts (ContactsScreen)
│   ├── Insights (Expenses, Health, Meds, Habits)
│   ├── Reminders (RemindersScreen, RemindersEditor, ReminderLinker)
│   ├── Assistant (AssistantScreen, ChatBubble, markdown)
│   ├── Disguise (CalculatorScreen, CalculatorActivity)
│   ├── Home (Grid + Tabs layouts, daily tips, recent activity)
│   ├── Settings (features, security, storage, AI, disguise, intruder)
│   └── Onboarding (welcome, auth, PIN, benchmark)
├── Bridge Layer (AI)
│   ├── GemmaRunner (LiteRT-LM engine, GPU/CPU, mutex)
│   ├── GemmaPrompts (notes AI templates)
│   ├── AssistantPrompts (chat system instruction)
│   ├── AssistantTools (search_notes, fetch_note, summarize_expenses)
│   ├── KnowledgeSnapshot (compact data builder, 8KB cap)
│   └── GemmaModelManager (download service)
├── Security Layer
│   ├── CryptoManager (AES-256-GCM, KEK/DEK)
│   ├── VaultRepository (encrypted photo/video storage)
│   ├── NoteRepository (encrypted notes)
│   ├── InsightsRepository (encrypted expenses/health/habits/meds/schedule)
│   ├── ContactRepository (encrypted contacts, self profile)
│   ├── PrivoraDatabase (SQLCipher)
│   ├── VaultLockManager (grace period, duress state)
│   ├── DuressManager (emergency PIN, wipe)
│   ├── PinRateLimiter (escalating cooldowns)
│   ├── IntruderCapture (Camera2 front capture on wrong PIN)
│   ├── BackupManager (export/import .paicbackup)
│   └── FolderManager (custom vault folders)
├── Service Layer
│   ├── ReminderScheduler (AlarmManager wrapper)
│   ├── ReminderReceiver (notification with Done/Skip)
│   ├── ReminderActionReceiver (mark done/skip + habit propagation)
│   ├── BootReceiver (re-register alarms)
│   ├── MissedSweepWorker (daily WorkManager)
│   ├── GemmaDownloadService (foreground model download)
│   ├── CrashHandler (local logging)
│   └── DeviceProfiler (benchmark)
└── Dependencies
    ├── CameraX 1.4.2
    ├── LiteRT-LM 0.10.0 (Gemma 4)
    ├── ONNX Runtime 1.21.0 (YOLOv8n)
    ├── ML Kit (scanner, OCR, barcode, translate, face detection)
    ├── SQLCipher (encrypted database)
    ├── WorkManager 2.9.1 (missed sweep)
    ├── AndroidX Biometric 1.1.0
    ├── Navigation Compose 2.9.0
    └── Kotlin 2.3.20, AGP 8.9.3, minSdk 26, targetSdk 35
```

## Commit History (Recent)

| Commit | Date | Description |
|--------|------|-------------|
| `bb519ce` | 2026-04-16 | Calculator disguise + Share-to-Privora + duress from calculator |
| `28cc0f0` | 2026-04-16 | AI Assistant + intruder alerts + notification privacy + home UX polish |
| `59c6f52` | 2026-04-15 | Insights redesign + standalone Reminders feature |
| `5db4a9f` | 2026-04-14 | Home layout: Grid/Tabs option + dynamic landing with AI tips |
| `38fcaa1` | 2026-04-13 | GPU backend with CPU fallback, OpenCL manifest fix, vision disabled |
| `42b14cf` | 2026-04-13 | Phase 3: Photo Intelligence DB + Vision (disabled pending 0.10.1) |
| `1dc8c36` | 2026-04-12 | Gemma 4 Phase 1: Notes Intelligence with on-device LLM |
