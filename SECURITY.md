# Security Policy

Privora is a privacy-first Android app that handles sensitive personal
data (photos, notes, contacts, password hints, health records). I take
security seriously and welcome responsible disclosure.

## Reporting a vulnerability

**Do not open a public GitHub issue for security bugs.**

Use one of these private channels:

- **Email**: `robomixes2025@gmail.com` — please put `[Privora security]`
  in the subject line so it doesn't get lost.
- **GitHub private security advisories**: open a private advisory under
  the repository's *Security* tab once the repo is public.

Include enough detail to reproduce: device + Android version, app
version (Settings → About), exact steps, expected vs actual behavior,
and any logs you can share. PoC code is welcome but not required.

## What to expect

- **Acknowledgement** within 7 days that I've received your report.
- **Triage update** within 14 days with a severity assessment and a
  rough timeline.
- **Coordinated disclosure**: I aim to ship a fix within **90 days** of
  the report, or sooner for severe issues. If I haven't shipped a fix
  in 90 days and you've been responsive throughout, you're free to
  publish your findings.
- **Credit**: with your permission, I'll add you to the Hall of Fame
  below in the release notes of the fixing version.

## Scope

The following are **in scope**:

- Cryptographic flaws — incorrect AES/GCM usage, weak PBKDF2 parameters,
  IV reuse, key material leaking outside the Android Keystore.
- Logic flaws that bypass the auth gate — biometric, PIN, duress, or
  the calculator disguise.
- Plaintext leakage of vault data — bytes outside the encrypted file
  format on disk, in logs, or via FileProvider URIs after a viewing
  session ends.
- Side-channel leaks of vault content into other apps via implicit
  intents, content providers, or backup rules.
- Anything that compromises the duress / calculator-disguise threat
  model so that "show empty" or "wipe" no longer behaves as documented.
- Memory issues that can be triggered remotely (e.g. via received Wi-Fi
  Transfer file or a malformed shared image).

The following are **out of scope** for the bug-bounty / acknowledgement
process — please report them but expect lower priority:

- Attacks that require an actively rooted device or a custom ROM that
  subverts the Android Keystore. Privora's security model assumes a
  stock, non-rooted device.
- Physical access to an unlocked phone whose user has just unlocked
  the vault — that's a phone-security problem, not a Privora problem.
- Social-engineering attacks on the user (phishing, coercion) — the
  duress PIN is a partial mitigation, not a guarantee.
- Attacks on third-party Android components (the OS keystore, biometric
  hardware, ML Kit, LiteRT-LM, SQLCipher) — please report those to the
  upstream maintainers.
- Issues in Privora forks, mods, or unofficial builds.

## Hall of Fame

*(empty — be the first)*

---

## What you can independently audit

Privora is open source under [AGPL-3.0](LICENSE). The full source is
public on GitHub. To validate claims yourself:

- Build from source: `./gradlew installFdroidDebug`. The F-Droid flavor
  excludes any IAP or Pro-only code.
- Compare the Play Store APK to a build from the public commit — the
  signing-key fingerprint will be published in the README once the
  reproducible-build pipeline is in place.
- Read [THREAT_MODEL.md](THREAT_MODEL.md) for what Privora does and
  doesn't claim to protect.

Thank you for helping keep Privora secure.
