# Privora — Project Status

Last updated: 2026-05-06

---

## Current Version

**Active branch**: `dev-v3-calibrate` — substantial WIP for the next minor release (Health feature + cycle tracker + calibration wizard expansion). Tracks `private/dev-v3-calibrate`, **not** `origin`.
**Default branch**: `main` at `ba45758` (v2.0.7 + STATUS update).
**Last public release**: **v2.0.7** (versionCode 9) — committed `a92acdc`, tagged, GitHub release published with `app-fdroid-release.apk` attached.
**Last published to Play**: v2.0.5 (versionCode 7) — v2.0.7 AAB is ready at `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab` but not yet uploaded to Play Console.
**Repo visibility**: **public** at https://github.com/robomixes/Privora.
**License**: AGPL-3.0-or-later (commercial license retained via CLA).
**CLA enforcement**: live via [CLA Assistant](https://cla-assistant.io/).

---

## Git Remotes & Mirror Management

| Remote | URL | Visibility | Purpose |
|---|---|---|---|
| `origin` | https://github.com/robomixes/Privora | public | Releases, F-Droid/IzzyOnDroid pickup, public marketing |
| `private` | https://github.com/robomixes/privora-private | private | WIP backup mirror — `dev-v3-calibrate` lives here until ready to ship |

**State invariants** (keep these true):
- `main` and all release tags (`v2.0.5`, `v2.0.6`, `v2.0.7`) exist identically on both remotes.
- WIP branches (`dev-v3-calibrate`, future `dev-*`) live **only** on `private`.
- `dev-v3-calibrate` tracks `private/dev-v3-calibrate` — `git push` with no args goes to private.
- Public never sees a force-push or rewritten history. Force-pushes only allowed on `private` WIP branches.

**Common workflows**:

| Goal | Command |
|---|---|
| Save WIP progress (no public exposure) | `git commit && git push` (defaults to `private/<current-branch>`) |
| Promote a branch to public | `git checkout main && git merge dev-v3-calibrate && git push origin main && git push origin --tags` |
| Sync a public update down to private mirror | `git fetch origin && git push private main && git push private --tags` |
| Add a new public release tag to private | `git push private vX.Y.Z` |
| Start a new WIP branch | `git checkout -b dev-<name> && git push -u private dev-<name>` |
| List branches on each remote | `git branch -r` (shows both `origin/*` and `private/*`) |

Reason for the split: AGPL is fine to develop in private — AGPL only triggers on *distribution*. Pushing to a private repo is not distribution. Pushing to `origin/main` or releasing the APK is. So WIP can stay private until quality + translations are ready.

---

## Distribution Status

| Channel | Status | Notes |
|---|---|---|
| **GitHub Releases** | ✅ Live | https://github.com/robomixes/Privora/releases/tag/v2.0.7 — APK attached (178 MB) |
| **Google Play Store** | ⏳ AAB ready, awaiting upload | Trimmed changelog at `fastlane/metadata/android/en-US/changelogs/9.txt` (≤500 chars for "What's new") |
| **F-Droid main repo** | ❌ Rejected | MR `f-droid/fdroiddata!37677` — maintainer linsui closed: "MLKit is not allowed". Policy line, not antifeature-fixable. |
| **IzzyOnDroid (apt.izzysoft.de)** | ⏳ Recommended next | More permissive third-party F-Droid-compatible repo; standard home for ML Kit apps. |

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

### Public-Source Launch (v2.0.0 → 2.0.5 — DONE)

| Item | Status | Notes |
|------|--------|-------|
| **Repository public on GitHub** | DONE | https://github.com/robomixes/Privora |
| **AGPL-3.0-or-later license** | DONE | LICENSE + 107 SPDX headers |
| **CLA infrastructure** | DONE | `CLA.md` (Harmony HA-CLA-I-Lite), CLA Assistant linked + Gist hosted, all PRs auto-gated |
| **Public docs** | DONE | README rewrite, `CONTRIBUTING.md`, `SECURITY.md`, `THREAT_MODEL.md`, `CHANGELOG.md`, `MARKETING.md` |
| **Build flavors** (`fdroid` / `playstore`) | DONE | Closed Pro module can later ship via Play flavor without contaminating F-Droid build |
| **Fastlane / F-Droid metadata** | DONE | `fastlane/metadata/android/en-US/` (title, descriptions, changelogs/7.txt) + `fdroid/com.privateai.camera.yml` ready for fdroiddata MR |
| **`v2.0.5` git tag pushed** | DONE | F-Droid `UpdateCheckMode: Tags` will auto-pick up subsequent releases |
| **GitHub release published** | DONE | Changelog body attached |
| **Default branch normalised** | DONE | `main` fast-forwarded to v5 work via PR #7; `v5-dev-gemma4` retired |

### Play Store Release Polish (v2.0.0 → 2.0.5 — DONE)

| Item | Status | Notes |
|------|--------|-------|
| versionCode 1 → 7, versionName 1.0.0 → 2.0.5 | DONE | Several Play-rejection cycles resolved; AAB at `app/build/outputs/bundle/playstoreRelease/` |
| 16 KB page-size compliance (arm64-v8a + x86_64) | DONE | Bumped ONNX Runtime 1.21.0 → 1.22.0 to fix `libonnxruntime4j_jni.so` alignment |
| Removed `USE_EXACT_ALARM` (Play policy: alarm/calendar apps only) | DONE | Reminders fall back to `SCHEDULE_EXACT_ALARM` (user-grantable) |
| Removed `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` + `WAKE_LOCK` | DONE | Switched AI model download from custom foreground service to system `DownloadManager` — also removes the Play Console foreground-service-justification video requirement |
| Custom `GemmaDownloadService` deleted | DONE | Replaced with `DownloadManager`-backed `GemmaModelManager` |
| Model file migrated to `getExternalFilesDir("models")` | DONE | One-shot migration on app start moves any existing internal copy |
| AI download lifecycle fixes (stuck → recoverable) | DONE | Disable / re-enable / Delete model now properly cancels in-flight; stall detector after 60 s of no progress with Retry button |
| Cellular fallback for AI download | DONE | `setAllowedOverMetered(true)` |
| Kotlin `removeFirst()` / `removeLast()` Android 15 fix | DONE | All call sites switched to `removeAt(...)` |
| Per-feature crash-recovery flag for vision (`vision_crashed`) | DONE | Vision SDK is unstable upstream; flag prevents crash loops if user retries |
| `gemma-4-E2B-it.litertlm` model URL still HuggingFace `litert-community` | DONE | Public URL, no auth required |
| Theme picker (System / Light / Dark) | DONE | Settings → Theme |
| Dark mode adaptation: home grid + bottom tabs | DONE | Per-feature accent overlay computed at render time |
| Home-screen widgets (4 new + 1 XL): Quick Note, Reminders, AI Assistant, Quick Access XL | DONE | Dark rounded shell + accent-colored icons |
| AI download dialog skip when model already on disk | DONE | One-tap re-enable |

### Sprint 2: Assistant Upgrades + Widgets (v1.9 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| AI streaming responses (token-by-token) | DONE | `bridge/GemmaRunner.completeStreaming`, `ui/assistant/AssistantScreen.streamAndCollect` |
| Progressive JSON `text` field decoder (no flashing wrapper) | DONE | `extractJsonStringPartial`, `TextFieldMarker` regex |
| AI action proposals — 7 types with one-tap confirm | DONE | `bridge/AssistantActions.kt`, `ChatBubble.ActionCard` |
| Reminder action (one-shot, ISO timestamp) | DONE | `ProposedAction.Reminder` → `ReminderScheduler.scheduleItem` |
| Expense action (8 categories, defaults to "Other") | DONE | `ProposedAction.Expense` → `InsightsRepository.saveExpense` |
| Note action | DONE | `ProposedAction.Note` → `NoteRepository.createNote` |
| Health record action (any subset of metrics) | DONE | `ProposedAction.HealthRecord` → `saveHealthEntry` |
| Contact action (name + optional phone/email/notes) | DONE | `ProposedAction.Contact` → `ContactRepository.saveContact` |
| Medication action | DONE | `ProposedAction.MedicationAction` → `saveMedication` |
| Habit action | DONE | `ProposedAction.HabitAction` → `saveHabits` |
| Action card states: Pending / Added ✓ / Failed → Retry / Dismissed | DONE | `ActionStatus` enum + `ActionCard` composable |
| Built-in PDF viewer (no external Intent handoff) | DONE | `ui/vault/PdfViewerScreen.kt` (PdfRenderer + LazyColumn) |
| Pinch-to-zoom on PDF viewer | DONE | `detectTransformGestures` + `graphicsLayer` |
| Status-bar inset on PDF top bar | DONE | `statusBarsPadding()` |
| Duress-mode crash on Insights entry — FIXED | DONE | Composition-time I/O isolated in nested try/catch + skip on duress |
| `_tobedeleted_` filter on Insights + PasswordHint repos | DONE | Defense-in-depth against background-deletion races |
| Home-screen widget: Quick Note (1×1) | DONE | `widget/QuickNoteWidget.kt` |
| Home-screen widget: Reminders (1×1) | DONE | `widget/RemindersWidget.kt` |
| Home-screen widget: AI Assistant (1×1) | DONE | `widget/AssistantWidget.kt` |
| Shared widget layout + custom rounded background | DONE | `layout/widget_action.xml`, `drawable/widget_bg.xml` |
| `__new__` deep-link to NotesScreen for Quick Note widget | DONE | `NotesScreen.openNoteId == "__new__"` |
| 13 new strings × 5 locales | DONE | EN/AR/FR/ES/ZH |

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

### v2.1 / dev-v3-calibrate (IN PROGRESS — private branch)

Substantial WIP committed on `private/dev-v3-calibrate`. Not yet released; not yet merged to public `main`.

| Area | What landed | Files |
|---|---|---|
| **Health top-level feature** | New "Health" route in home grid + bottom-tab. Three sub-tabs: Vitals (renamed from HealthTab), Medications (moved), Cycle (new). Shared profile filter. Mirrors Insights' auth + duress patterns. | `ui/health/HealthScreen.kt`, `ui/health/VitalsTab.kt`, `ui/health/MedicationsTab.kt` (moved from `ui/insights/`) |
| **Cycle / period tracker (v1.1)** | Monthly calendar with chevron navigation, day-of-month labels, 12-cycle forward prediction projection, today-only-when-current ring, period fills + ringed estimates, 5 canonical symptoms, first-use non-medical disclaimer. Stored as `${id}.cycle.enc` files (same DEK). | `ui/health/CycleTab.kt`, `ui/health/CycleCalendar.kt`, `ui/health/AddCycleDialog.kt`, `ui/health/CycleDisclaimerDialog.kt`, `security/InsightsRepository.kt` (CycleEntry + CRUD + predictNextPeriod) |
| **Insights cleanup** | Health and Medications cases removed from InsightsScreen — Insights now hosts only Expenses + Habits. | `ui/insights/InsightsScreen.kt` |
| **Calibration wizard (expanded)** | First-run + Settings-rerun guided flow. **13 steps**: 5 actions (Layout / Modules / Device test / Emergency PIN / AI download) + 7 info pages (Vault, Health, Backup, AI, Emergency PIN, Calculator, Intruder) + Finish. Refresh icon in Settings top-bar re-runs the wizard. | `ui/calibrate/CalibrationWizardScreen.kt`, `ui/calibrate/WizardManager.kt` |
| **Grid header redesign** | LargeTopAppBar replaced with compact TopAppBar. Reminders bell with count badge → today's-reminders popup. Tip card upgrades to AI-generated tip when Gemma is available. Static-tip pool refreshed with 8 new AI-focused entries. | `ui/home/HomeScreen.kt`, `ui/home/HomeLandingData.kt` |
| **Camera pinch-to-zoom** | CameraX `cameraControl.setZoomRatio` wired via `detectTransformGestures`. "1.5x" overlay fades after 900ms. Photo + video. | `ui/camera/CaptureScreen.kt`, `ui/camera/CameraPreview.kt` |
| **Settings polish** | "Privora version" auto-tracks `BuildConfig.VERSION_NAME` (no more stale 1.0.0). Refresh icon in top bar re-runs the calibration wizard. | `ui/settings/SettingsScreen.kt`, all `strings.xml` (settings_privo_desc → `%1$s`) |
| **Profile-link dialog** | Fully translated (no more English fallback). Empty state offers one-tap "Open People" shortcut to add a contact. | `ui/health/HealthScreen.kt#LinkHealthProfileDialog` |
| **Strings** | ~50 new keys × 5 locales (EN/FR/ES/ZH/AR). **Arabic needs human review** for menstruation vocabulary cultural sensitivity. | All `values-*/strings.xml` |
| **Backup/Restore** | Cycle entries auto-included via existing `vault/...*.enc` recursive walk in BackupManager. No code change. Verified. | `security/BackupManager.kt` (no change) |
| **AI Assistant: bigger input + writing chips** | OutlinedTextField raised to `minLines=4` / `heightIn(min=110.dp)` / `maxLines=18`. Below the input: 8 quick-action chips that wrap typed text in a directive and send — Grammar / Rewrite / Summarize / Formal / Simpler / Translate (with language picker) / Expand / Bullets. | `ui/assistant/AssistantScreen.kt` |
| **AI Assistant: bubble action row** | Below every assistant reply: discoverable Copy / Share / Save-as-note icons. Save creates a note tagged `[assistant]`, deduplicated — once-saved replies show a green checkmark and the button disables to prevent duplicate notes from re-taps. | `ui/assistant/ChatBubble.kt`, `ui/assistant/AssistantScreen.kt` |
| **AI Assistant: longer context** | Per-message history truncation bumped 500 → 1500 chars in `formatTurn`. Multi-turn refinements on email-length text now actually have the prior reply in scope; previously the model only remembered the first paragraph. | `bridge/AssistantPrompts.kt:81` |
| **Vault: gallery scroll preserved** | `LazyListState` hoisted above the `when (page)` switch so the gallery survives the gallery → viewer → gallery round-trip. With 1500 photos the user no longer lands at the top after peeking at one. | `ui/vault/VaultScreen.kt` (galleryListState) |
| **Vault: viewer polish** | Viewer background back to black (standard photo-viewer convention). `Crossfade(100ms)` between images on swipe / tap-next so transitions are smooth instead of a hard pop. | `ui/vault/VaultScreen.kt` (VIEWER branch) |
| **Vault: trash batch fix** | New `moveToTrashBatch(photos)` in `VaultRepository` — per-item try/catch + load-and-save the encrypted index ONCE (not 73× per item). `deletePhotos` runs on `Dispatchers.IO` so the UI doesn't freeze, and surfaces honest "Moved X of N" / orphan-warning toasts when not all succeed. Fixes a silent-data-loss bug where 73 multi-deletes could fail mid-loop and leave items removed-from-view-but-not-in-trash. | `security/VaultRepository.kt` (`moveToTrashBatch`), `ui/vault/VaultScreen.kt` (`deletePhotos`) |
| **Vault: viewer delete sweeps to next** | Deleting the displayed image now navigates to the next item in the folder (or previous, if it was the last one). Only falls back to the gallery when the list is empty after delete. | `ui/vault/VaultScreen.kt` (delete dialog confirm handler) |
| **Vault: gallery loads progressively** | `openCategory` flips `page = GALLERY` immediately, then streams thumbnails 40 at a time on `Dispatchers.IO`. Previously a 1500-photo open showed the lock-styled categories page for ~3 seconds while every thumbnail decrypted. Now the gallery appears instantly with grey placeholders and rows fill in progressively. Streaming bails out if the user navigates away mid-decode. | `ui/vault/VaultScreen.kt` (`openCategory`) |
| **Source EXIF preserved on import** | New `ExifUtils.readMetadata(bytes)` reads `dateTaken / width / height / orientation / gpsLat / gpsLng` from raw bytes BEFORE `BitmapFactory.decodeByteArray` strips them. Saved as an encrypted `<id>.meta.enc` JSON sidecar next to each photo. Sidecar is carried with the photo through `moveToFolder`, `moveToTrash`, `moveToTrashBatch`, `restoreFromTrash`; deleted with `permanentDeleteFromTrash`, `emptyTrash`, `autoPurgeTrash`; auto-included in backups via the existing `.enc` recursive walk. `setLastModified(dateTaken)` on the photo file makes `listPhotos` sort correctly without decrypting any sidecars. The encrypted JPEG payload itself stays metadata-free, so sharing from the vault still leaks nothing — privacy invariant preserved by design. | `util/ExifUtils.kt`, `security/VaultRepository.kt` (`PhotoMetadata`, `saveMetadataSidecar`, `loadMetadata`), `ui/vault/WifiTransferScreen.kt`, `ui/vault/VaultScreen.kt` (3 import paths) |
| **Vault: photo details dialog surfaces source metadata** | The existing details dialog (Info icon in selection mode + viewer top bar) now shows an "Original metadata" section when the sidecar carries data — original dimensions and GPS coordinates formatted as `48.8520°N, 2.3500°E`. Lazily decrypted, cached via `remember(item.id)`. | `ui/vault/VaultScreen.kt` (details dialog), `formatGps` helper |

**Pending before public ship**:
- Real-device testing of cycle tracker across multiple cycles
- Arabic translation review for menstruation vocabulary
- Version bump (likely 2.1.0)
- Updated changelog + AAB build
- Merge to `main`, tag, push to `origin`, GitHub release, Play upload

---

## Known Issues / Limitations

| Issue | Impact | Status |
|-------|--------|--------|
| Gemma 4 vision still crashes (tested 0.10.2 + 0.11.0-rc1) | Photo Q&A disabled | Native SIGSEGV in liblitertlm_jni.so on Pixel 9a / e2b model. Crash-flag guard in place. |
| Intruder capture device-dependent | Camera2 headless may fail on some devices | Plain JPEG fallback |
| Wi-Fi transfer key derivation is custom mixing | Not PBKDF2, uses 10K-round iterative mix | Acceptable for ephemeral transfers |

---

## What's Next

### Not Started

| Feature | Priority | Effort |
|---------|----------|--------|
| Upload v2.0.7 AAB to Play Console | High | 5 min — paste trimmed changelog, attach AAB, roll out |
| IzzyOnDroid submission (replaces F-Droid main) | High | ~30 min — same yaml format, submit at `gitlab.com/IzzyOnDroid/repo` |
| Close F-Droid main MR with polite reply to linsui | Low | 1 min — pivoting to IzzyOnDroid given ML Kit rejection |
| Multi-locale Fastlane metadata (fr / es / zh / ar) | Medium | 1-2 hours; en-US already done |
| Lawyer review of `CLA.md` | Recommended | Before accepting first non-trivial external PR |
| `.github/FUNDING.yml` | Low | Pending sponsor / OpenCollective accounts |
| Audio transcription in notes (Vosk or Android on-device) | Medium | Deferred — privacy approach pending |
| Gemma vision (photo Q&A) | Blocked | Tested 0.10.2 + 0.11.0-rc1 — both hard-fault. Filed upstream; revisit on next release. |
| Cross-device sync (E2E encrypted, AGPL server) | High | 2-3 weeks (Phase 3 of monetization plan) |
| Iterate AI action prompts based on real usage | Medium | Ongoing |
| Replace "Anas" → full legal name in LICENSE / CLA / SPDX | Low | One find/replace when entity / name decision settles |
| Publish Play signing-key SHA-256 fingerprint in README | Low | Helps reproducible-build verifiers |

### Play Store Readiness

| Item | Status |
|------|--------|
| App name: Privora | DONE |
| Version management | DONE (versionCode 9 / 2.0.7) |
| Privacy policy (PRIVACY.md) | DONE |
| Adaptive icon | DONE |
| ProGuard/R8 | DONE |
| Signing config | DONE |
| 16 KB page-size compliance | DONE |
| FOREGROUND_SERVICE_DATA_SYNC justification (no longer needed) | DONE — removed permission |
| USE_EXACT_ALARM removed (policy compliance) | DONE |
| Kotlin Android 15 collision fix (`removeFirst/Last`) | DONE |
| v2.0.7 AAB built + signed | DONE — `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab` (~221 MB) |
| v2.0.7 changelog (≤500 chars for "What's new") | DONE — `fastlane/metadata/android/en-US/changelogs/9.txt` |
| v2.0.7 upload to Play Console | TODO (manual) |
| Data Safety declaration | TODO (manual in Play Console) |
| Screenshots + feature graphic | TODO (manual) |
| Store listing description | DONE (text in `fastlane/metadata/android/en-US/`) — paste into Play Console |

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
| `a92acdc` | 2026-05-04 | v2.0.7: voice-note quality batch (VOICE_RECOGNITION audio source + toggle, mic permission gate, "Play all" auto-advance, draft persistence on auto-lock, defensive save, locale-safe Wi-Fi PIN/IP, Settings → Advanced translations, password generator extracted) |
| `791ac02` | 2026-05-03 | v2.0.6: fix crash on PHONE_LOCK onboarding (empty-PIN PBKDF2) + libbarhopper packaging fix for 32-bit ARM |
| `8740684` | 2026-05-03 | Merge PR #7: v5 work merged into `main`; default branch fully current |
| `b19f5b8` | 2026-05-02 | Add F-Droid + Fastlane metadata + CHANGELOG.md |
| `11ad369` | 2026-05-02 | v2.0.5: replace Kotlin removeLast() with removeAt() (Android 15 fix) |
| `4052fe2` | 2026-05-02 | v2.0.4: AI download — allow metered, detect stalls, expose Retry |
| `6aab38a` | 2026-05-02 | v2.0.3: rebuild for Play with stuck-download fix |
| `471ee1d` | 2026-05-02 | Fix stuck AI model download not recovering on retry |
| `b063faa` | 2026-05-02 | v2.0.2: replace foreground-service download with system DownloadManager — drops 3 permissions, no Play video required |
| `ce349f3` | 2026-05-02 | v2.0.1: drop USE_EXACT_ALARM (Play policy: alarm/calendar apps only) |
| `e1e5224` | 2026-05-02 | v2: bump versionCode + ONNX Runtime 1.22.0 for 16 KB page-size compliance |
| `1a02bce` | 2026-05-02 | Vision still crashes on 0.11.0-rc1 — keep disabled |
| `f0d2df9` | 2026-05-02 | Skip download prompt when AI model is already on disk |
| `3d07f59` | 2026-05-02 | Bump LiteRT-LM to 0.11.0-rc1 — 0.10.2 vision still crashes |
| `ac032dd` | 2026-05-02 | Re-enable Gemma vision with crash-flag safety net |
| `9078180` | 2026-04-29 | Apply XL widget logic to all 1×1 widgets |
| `237c16c` | 2026-04-29 | Make widgets fresh + colorful (initial pass) |
| `936a4a0` | 2026-04-29 | Theme picker (System / Light / Dark) in Settings |
| `83e10a4` | 2026-04-29 | 4×1 Quick Access XL widget |
| `17bff83` | 2026-04-29 | Dark mode adaptation for home grid + bottom tabs |
| `1f5b6f2` | 2026-04-28 | Add SECURITY.md + THREAT_MODEL.md |
| `f0a931f` | 2026-04-28 | Add CLA + CONTRIBUTING + PR template (CLA Assistant workflow) |
| `b5cb356` | 2026-04-28 | License the code under AGPL-3.0 |
| `d04297a` | 2026-04-28 | Add fdroid / playstore product flavors |
| `e18b559` | 2026-04-28 | Sprint 2: AI streaming, action proposals (7 kinds), home-screen widgets |
| `1e6cc25` | 2026-04-27 | Fix PDF viewer back button hidden behind status bar |
| `0ba1630` | 2026-04-27 | Built-in PDF viewer (no external app handoff) |
| `51d92d3` | 2026-04-27 | Fix duress-mode crash on Insights entry |
| `0a9e35c` | 2026-04-27 | Change PIN flow + PBKDF2 hardening for app PIN |
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
