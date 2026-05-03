# Privora — Transparency-First Monetization Strategy

## Context

Privora is already feature-rich: on-device AI assistant (Gemma 4), encrypted vault (photos / videos / PDFs / files / hidden folder), notes, contacts (SQLCipher), reminders, camera with intruder capture, Wi-Fi transfer with browser-side AES, calculator disguise + duress PIN, password hints, translation, local backup, multi-language (5 locales), FLAG_SECURE, etc. The repo is currently closed-source; no monetization infrastructure; no servers; not yet released publicly. iOS / Windows / Linux on the roadmap.

Decisions already taken:
- **Grow users first**, monetize gradually.
- **Open core** license model.
- **Willing to run servers** for optional zero-knowledge E2E sync.
- **All currently-built features stay free forever** in the OSS build. Pro is for things that cost money to run or are built *after* launch.

The tension: a product sold on "your data stays yours" can't use any monetization model that compromises that promise (ads, analytics, data resale, closed crypto). It has to earn money the same way it earns trust — by being auditable. Reference playbook: **Bitwarden, Standard Notes, Proton, Joplin** — all open-source, profitable, trusted.

---

## The free tier — "astonishingly generous" by design

The free build is also the marketing. Word-of-mouth in privacy communities is worth more than any ad budget, and it only happens if the free app genuinely delivers.

**Everything currently in the app stays free, unlimited, and AGPL forever:**
- On-device AI assistant (Gemma 4 via LiteRT)
- Encrypted vault: photos, videos, PDFs, arbitrary files, hidden folder, folders + multi-select, pinch-to-zoom
- Notes, contacts, reminders, insights
- Camera + intruder capture
- Wi-Fi transfer with browser-side AES-256-CTR
- Calculator disguise + duress PIN
- Password hints + generator + clipboard auto-clear
- Translation (ML Kit + AI grammar fix + alternatives)
- Local encrypted backup / restore (including SQLCipher DB)
- 5 locales, FLAG_SECURE, all platform-security features

No storage caps, no item caps, no nags, no "upgrade" popups. Settings has a single unobtrusive "Support development" row.

## The Pro tier — only things that cost money or are genuinely new

Pro exists to fund the project and cover real costs. It must never retroactively gate a free feature.

**Server-backed (subscription, recurring revenue):**
- **Privora Sync** — zero-knowledge E2E encrypted cross-device sync. Server stores ciphertext only; keys never leave devices. Server code is AGPL and self-hostable. ~€3/mo or €25/yr; family plan.
- **Privora Cloud Backup** — same infrastructure, E2E encrypted off-site backup of the vault (optional addon or bundled with Sync).

**Local but "above-and-beyond" (one-time IAP or bundled with Pro):**
- Premium / larger on-device AI models (bigger Gemma variants, specialized models).
- Advanced duress scripts (multiple duress PINs, custom actions beyond the built-in ones).
- Future RAW / HDR+ camera modes, on-device voice features.
- Themes, icon packs.
- Bulk AI operations & automation (e.g. batch-tag all photos, scripted workflows).
- Early access to features under development.

**Pricing shape:**
- Privora Pro: one-time **€15 lifetime** *or* **€2/mo** subscription — user chooses.
- Privora Sync: **€3/mo** or **€25/yr** (separate, because it has recurring server cost).
- Bundle: Pro + Sync ≈ **€35/yr**.

---

## The three pillars

### 1. License: AGPL-3.0 core + closed Pro module

- Core app under **AGPL-3.0**, not MIT/Apache. AGPL stops a bigger player from forking, adding a backend, and out-marketing you. Bitwarden and Standard Notes both use AGPL for exactly this reason.
- **Pro module** is a separate closed-source Gradle module loaded only in the `playstore` flavor. The `fdroid` flavor doesn't compile it in at all — F-Droid ships a pure-OSS build, no billing code.
- **Sync server** (when it lands) is its own repo, AGPL-3.0, self-hostable. Paying customers pay for *convenience*, not *possibility*. This is the trust anchor.

### 2. Distribution & revenue, sequenced for growth-first

**Phase 1 — launch & grow (months 0–6)**
- Ship 1.0 free on **F-Droid** (pure OSS build) and **Play Store** (same code + Pro module stubs, all features free).
- Enable **GitHub Sponsors + OpenCollective + Liberapay**. Non-nagging "Support" entry in Settings.
- Accept **sponsored features / translations** from aligned orgs (EFF, journalism NGOs, privacy companies) under a public sponsorship policy that forbids feature compromises.
- Target: ~10k active users, visible community, landing on F-Droid "featured."

**Phase 2 — Privora Pro (months 6–12)**
- Ship Pro: premium AI models + themes + bulk AI/automation + advanced duress. One-time IAP or subscription, user picks.
- No existing feature is moved behind Pro.

**Phase 3 — Privora Sync (months 12–18)**
- Zero-knowledge sync + optional cloud backup. Separate subscription. Server code public & self-hostable.
- This is where recurring revenue starts — the lever that funds full-time work and platform expansion.

**Phase 4 — business tier & platforms (months 18+)**
- Teams / enterprise licensing (admin console, deployment, support SLA).
- Sponsored feature-development contracts.
- iOS / Windows / Linux builds, funded by Pro + Sync revenue.

### 3. Transparency mechanisms (the trust contract)

Not optional — these are what make the model defensible. Each one rebuts a specific reason a privacy-conscious user would doubt you:

- **Public repo, AGPL-3.0**, full history from day one.
- **Reproducible builds** — users (and F-Droid) can verify the Play Store APK matches the public commit.
- **Published signing-key fingerprint** in README.
- **Plain-language threat model & privacy policy**: what Privora sees, what servers see, what nobody sees. One page, not legalese.
- **Crowd-funded external security audit** of the crypto code before Sync launches. Publish the report unchanged.
- **Warrant canary** + Signal-style **transparency report** once Sync is live.
- **No analytics, no telemetry** — not even "anonymized." Crash reports are local-only unless the user taps "send this one."
- Incorporate as a small entity; consider a **B-Corp or non-profit foundation** later — structural signal that profit isn't the sole driver.

---

## Anti-patterns to reject early

- Analytics SDK "for product decisions." One slip = brand story dead.
- Retroactively gating existing free features. Breaks the compact.
- Artificial free-tier limits on storage, item count, or any security feature.
- VC funding that demands growth-at-any-cost. Always ends in enshittification for privacy products.
- Sponsorships with "just add this small SDK" strings attached.
- Closing source of the server "for security." The opposite holds here.

---

## Concrete first moves (this month)

1. **Pick the license**: AGPL-3.0 for the core, a commercial EULA for the Pro module. Add `LICENSE` at repo root and per-file headers.
2. **Make the repo public** at `github.com/robomixes/Privora`. Rewrite README with threat model summary, build instructions, signing-key fingerprint, donation links.
3. **Set up funding**: `.github/FUNDING.yml` → GitHub Sponsors, OpenCollective, Liberapay. No Patreon.
4. **Add build flavors**: `fdroid` (pure OSS, no IAP code, links only to sponsors) and `playstore` (OSS + future Pro module). F-Droid inclusion is the single strongest credibility signal available for an Android privacy app.
5. **Publish to F-Droid**: write the reproducible-build manifest.
6. **Publish to Play Store** with the same codebase. Free. No IAP yet.
7. **Public sponsorship policy** (one page): what you will and won't accept money for.
8. **Privacy policy + threat model** as Markdown in the repo, linked from Settings → About.

Everything from Phase 2 onward waits until real usage tells you what people want to pay for. Don't design Pro features in the dark.

---

## Critical files / areas to touch when executing

- **Repo root**: `LICENSE` (AGPL-3.0), `README.md` rewrite, `SECURITY.md`, `PRIVACY.md`, `THREAT_MODEL.md`, `.github/FUNDING.yml`, `CONTRIBUTING.md`.
- **Build system**: `app/build.gradle.kts` — add `fdroid` / `playstore` product flavors. F-Droid flavor excludes any IAP / billing / closed-module code entirely.
- **Settings screen**: add "Support development" row. In the `fdroid` flavor it links only to GitHub Sponsors / OpenCollective.
- **F-Droid metadata**: `metadata/en-US/` YAML + Fastlane directory for store listings (reused across F-Droid and Play Store).
- **Future**: `privora-sync-server` as a separate repo, AGPL-3.0 — not part of the Android repo.

## Verification (how you'll know it's working)

- **Trust**: app lands on F-Droid without policy exceptions. Privacy bloggers and r/privacy can be pointed at the repo and confirm claims.
- **Growth**: track install counts on Play Store + F-Droid (the only "analytics" needed — they come from the stores, not the app). 1k installs in month 1 is realistic for a featured privacy app.
- **Funding floor**: donations + sponsored translations should cover direct costs (developer account fees, domain, eventual audit) before Pro launches.
- **Pro viability gate**: don't launch Pro until ≥ 5% of active users have engaged with the "Support" link. Weak signal that paid features will convert.
- **Sync viability gate**: don't build Sync until the community is *asking* for it — validated by GitHub issues and in-app feedback, not assumption.
