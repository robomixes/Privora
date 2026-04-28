# Privora — Threat Model

Plain-language description of what Privora protects against, what it
does not, and the cryptographic posture under the hood. This document
is part of the public trust contract referenced in [README.md](README.md).

## Summary

Privora is designed for users who want their personal data — photos,
notes, contacts, password hints, health records — to stay on their own
device. Everything is encrypted at rest with hardware-backed keys.
Nothing is sent to any cloud or analytics endpoint.

The app makes a defensible job of protecting **data at rest** and
**data exposed during normal device use**. It does **not** try to
defend against an attacker who has root, custom firmware, or runtime
access to a phone whose user has just unlocked the vault.

---

## What Privora protects against

### Cloud-side breaches and tracking
There is no Privora cloud. Photos, notes, contacts, password hints,
reminders, and AI conversations never leave the device. There is no
account, no telemetry, no analytics SDK, no third-party crash reporter
that phones home. The only network access the app makes is:

- **Wi-Fi Transfer** (user-initiated, one-shot) — runs a local HTTP
  server on the phone that another device on the same Wi-Fi can connect
  to. Files are encrypted in the browser before leaving the source
  device. The server runs only while the user has the screen open.
- **Gemma model download** (user-initiated, one-shot) — fetches the
  on-device AI model file the first time the user enables AI. No data
  is sent; it's a read-only download from a public URL.
- Otherwise: no outbound traffic.

### A passive attacker who gets the phone's storage offline
- The vault directory contains only AES-256-GCM-encrypted files.
- The DEK (the symmetric key actually used to encrypt files) is wrapped
  by a KEK held in the Android Keystore — hardware-backed (TEE /
  StrongBox where available). The KEK never leaves the secure enclave.
- The contacts and photo-index databases are encrypted with SQLCipher.
- The app PIN is stored as a PBKDF2-SHA256 hash (10 000 iterations,
  256-bit key, 16-byte salt). No plaintext PIN on disk.
- The duress PIN uses the same hashing scheme.
- Backups (`.privorabackup`) are encrypted under a backup-time-
  derived key wrapped by a user-chosen passphrase.

### A thief who finds the phone unlocked
- The vault auto-locks after a configurable grace period of inactivity.
- The vault, notes, contacts, insights, reminders, and password hints
  each independently re-prompt for auth on entry once the grace expires.
- `FLAG_SECURE` is enabled by default — screenshots and screen recording
  are blocked, including the system recents preview.
- Notifications use `VISIBILITY_SECRET` so reminder content doesn't
  leak on the lock screen.

### Shoulder-surfers and observers
- PIN entry uses Android's `NumberPassword` keyboard with a redacted
  visual transformation by default.
- Vault content lives behind one tap — it's never visible from the
  home screen, recents, or notifications.

### Coerced unlock (duress)
- A separate **duress PIN** can be configured. Entering it instead of
  the real PIN unlocks Privora into one of two pre-chosen modes:
  - **Empty**: shows an empty vault and empty notes; real data is
    untouched and remains accessible later via the real PIN.
  - **Wipe**: irreversibly destroys the KEK, marks all vault files for
    background deletion, regenerates fresh keys so the app appears
    fresh-installed. Real data is unrecoverable from this point.
- The **calculator disguise** can be enabled as a launcher icon; the
  app appears as a working calculator. Typing the PIN followed by `=`
  silently launches Privora.

### Abuse via Android share / intent surface
- The shared image / video / PDF / text intent path imports content
  into the encrypted vault or notes, never to public storage.
- The built-in PDF viewer renders decrypted PDFs from the app's private
  cache (no FileProvider grant during viewing).
- The Wi-Fi Transfer server only accepts uploads after a session-bound
  PIN check; three wrong PINs lock the server out.

---

## What Privora does *not* protect against

### A rooted device, custom ROM, or active malware on-device
If an attacker has root or has installed a kernel module / accessibility-
service-style malware that can read app memory, no app-level encryption
helps. Privora assumes a stock, non-rooted Android device.

### An attacker who has the user's PIN
The PIN is the gate. If they have it (shoulder-surf, brute force on a
device with weak attempt limits disabled, social engineering), they get
the data. The duress PIN is a partial mitigation when coercion is
expected.

### Hardware compromise of the secure enclave
The TEE / StrongBox is a third-party component. Privora trusts it. If
the enclave is broken (e.g. exfiltration of KEK material via a hardware
bug), Privora's at-rest encryption falls with it.

### Screen recording on a compromised OS
`FLAG_SECURE` is honoured by stock Android. A modified OS or a
screen-recording app with elevated privileges can bypass it.

### Voluntary handover of data
If the user shares a photo / note / vault export through the system
share sheet, that content leaves Privora's control.

### The user's own backup choices
A user-exported `.privorabackup` is only as safe as its passphrase.
Weak passphrase → weak backup.

---

## Cryptographic posture

| Asset | Mechanism |
|------|------|
| Vault files (photos, videos, PDFs, notes, hints, insights) | AES-256-GCM, chunked for files > 16 MB |
| Data Encryption Key (DEK) | Random 256-bit, wrapped by KEK |
| Key Encryption Key (KEK) | Android Keystore, hardware-backed (StrongBox where available) |
| Contacts + photo index DB | SQLCipher (AES-256-CBC by default) |
| App PIN | PBKDF2-HMAC-SHA256, 10 000 iterations, 256-bit, 16-byte salt |
| Duress PIN | Same as app PIN |
| Backup file | AES-256-GCM, key derived from passphrase via Argon2id |
| Wi-Fi Transfer payloads | AES-256-CTR in the browser, key derived from PIN + salt |
| AI inference | Entirely on-device (LiteRT-LM / Gemma 4) |

## Third parties involved in privacy-sensitive paths

- **Android Keystore** (Google / device OEM) — holds the KEK.
- **SQLCipher** (Zetetic) — encrypts the contacts + photo-index DB.
- **LiteRT-LM** (Google) — runs the on-device AI model. The model
  itself is a Gemma 4 weights file; no telemetry from inference.
- **ML Kit** (Google) — used for OCR, document scanning, barcode
  scanning, translation. Runs on-device; per Google's docs, no audio
  / image data is sent off-device for these specific APIs in the
  configurations Privora uses.
- **CameraX, AndroidX Biometric, WorkManager** (Google / AOSP) —
  standard platform components.

There are deliberately **no analytics SDKs, no crash reporters, no ad
networks**, and no third-party libraries that issue network calls.

---

## Out of scope (for now)

- Cross-device end-to-end encrypted sync — designed but not yet shipped
  (planned for v2.0; will be a separate AGPL-licensed server).
- Reproducible-build attestation — planned ahead of F-Droid submission.
- Independent third-party security audit — planned to fund via
  donations + Pro revenue.

This document will be updated as the threat model evolves and as
features ship. The current commit hash + last-modified date is
authoritative.
