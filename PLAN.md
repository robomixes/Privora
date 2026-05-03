# Privora — Roadmap

**Core value proposition**: "AI camera and personal vault that never sends your data anywhere."

**Architecture**: Native Android (Kotlin) + Jetpack Compose + CameraX + LiteRT-LM (Gemma 4) + ONNX Runtime + ML Kit + AES-256-GCM encryption

**GitHub**: https://github.com/robomixes/private-ai-camera

---

## Completed Releases

### v1.0 — Core Features
Camera, encrypted vault, secure notes, document scanner, QR scanner, translation, tools, backup/restore, onboarding, emergency PIN, EXIF stripping, face blur, screenshot protection, auto-lock.

### v1.1 — Extended Features
Photo editor, note attachments + audio, encrypted contacts with face linking, 5-language localization (EN/AR/ES/FR/ZH), crash handler, home screen widget.

### v1.2 — On-Device AI (Gemma 4)
LiteRT-LM engine (GPU-first, CPU fallback), 2.7GB model download, Notes AI (summarize, rewrite, extract, continue, grammar), daily AI tips, vault photo descriptions (text-only, vision blocked by LiteRT-LM bug).

### v1.3 — Insights & Reminders
Profile unification across Insights tabs, Medications tab with linked reminders, Habits with streaks + calendar, standalone Reminders feature (one-time + recurring, AlarmManager, boot persistence, missed sweep), home layout Grid/Tabs with persistent bottom bar.

### v1.4 — AI Assistant
Unified chat surface with ✨ entry in home top bar, knowledge snapshot (8KB), 3 tools (search_notes, fetch_note, summarize_expenses), markdown rendering, dynamic temperature, persistent session, data refs with deep-linking.

### v1.5 — Market Readiness
Notification privacy (VISIBILITY_SECRET), Share-to-Privora (ACTION_SEND), intruder alerts (Camera2 front capture on wrong PIN), calculator disguise (working calculator + PIN+= unlock + duress support).

---

## Next Up

### v1.6 — AI Polish
| Feature | Effort | Priority |
|---------|--------|----------|
| Streaming responses (token-by-token display) | 1 day | High |
| Voice input (on-device SpeechRecognizer, API 31+) | 2 days | Medium |
| AI action proposals (create reminder, add expense from chat) | 2-3 days | Medium |

### v1.7 — Engagement
| Feature | Effort | Priority |
|---------|--------|----------|
| Home screen widgets (quick note, locked capture) | 1-2 days | Medium |
| Audio transcription in notes | 2 days | Medium |
| Smart home suggestions ("you usually log breakfast now") | 2 days | Low |

### v2.0 — Vision + Extended AI
| Feature | Effort | Priority |
|---------|--------|----------|
| Gemma 4 vision (photo Q&A) | Blocked | Waiting for LiteRT-LM 0.10.1 |
| Plant/animal ID via vision | Blocked | Requires vision fix |
| Scene description (accessibility) | Blocked | Requires vision fix |
| Semantic photo search (embeddings) | 1 week | Medium |

### v3.0 — Ecosystem
| Feature | Effort | Priority |
|---------|--------|----------|
| Encrypted cloud backup (auto-sync) | 2-3 weeks | High |
| Cross-device sync | 3-4 weeks | High |
| Desktop app (Compose Multiplatform) | Months | Future |
| iOS port (SwiftUI) | Months | Future |

### v4.0 — Zero Google
| Feature | Effort | Priority |
|---------|--------|----------|
| Custom document scanner (OpenCV) | 2 weeks | Low |
| Custom translation (local ONNX) | 2 weeks | Low |
| Custom OCR (local model) | 1 week | Low |
| Remove Google Play Services dependency | 1 month | Low |

---

## Network Policy

| Use | Policy |
|-----|--------|
| AI inference | **NEVER** — Gemma 4 runs on-device |
| User data | **NEVER** — all local, AES-256-GCM encrypted |
| Web search | Opens **external browser** via intent |
| Models | YOLOv8n **bundled in APK**. Gemma 4 **optional download** (2.7GB). Translation models via Play Services |
| Analytics | **NONE** — no Firebase, no Crashlytics, no telemetry |

---

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
                                           ├── Notes: {id}.note.enc (JSON)
                                           ├── Insights: per-file .enc (expenses, health, habits, meds)
                                           └── Reminders: vault/reminders/items/*.sched.enc

Backup: DEK wrapped with PBKDF2-derived key (600K iterations)
        .enc files zipped as-is (no re-encryption)
        Single .paicbackup file

Emergency Wipe: Delete KEK → data unreadable → fresh keys → background file cleanup
Calculator Duress: PIN+= in calculator → wipe → launches Privora in empty state
```
