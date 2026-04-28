# Contributing to Privora

Thank you for your interest in improving Privora. This document covers
how to build the project, how to send a pull request, and the legal
paperwork required for your contribution to be merged.

## Quick start

```bash
git clone https://github.com/robomixes/Privora.git
cd Privora

# Build and install the Play Store flavor (default — same code as F-Droid,
# plus future paid features once they exist)
./gradlew installPlaystoreDebug

# Or build the F-Droid flavor (pure OSS, no IAP / billing code)
./gradlew installFdroidDebug
```

The project uses Kotlin 2.3.20, AGP 8.9.3, minSdk 26, targetSdk 35.
A working Android SDK + a connected device or emulator is required.

## Building both flavors

The project ships two product flavors: `fdroid` (pure-OSS build for
F-Droid) and `playstore` (same code today; will host the closed Pro
module in the future). Both must compile cleanly:

```bash
./gradlew assembleFdroidDebug assemblePlaystoreDebug
```

Anything you add to the OSS core must work in both flavors. Pro-only
code (when it lands) lives behind a `BuildConfig.IS_FDROID` guard or in
a flavor-specific source set.

## Style

There's no formal style guide yet — match the surrounding code.
Concretely:

- Kotlin idiomatic. Prefer expression bodies, `val` over `var`, sealed
  classes over enums when you need data per-case, immutable data classes.
- Compose UI: stateless composables where possible, hoist state up.
- Privacy-first. Anything that touches user data goes through
  `CryptoManager` (vault files), `PrivoraDatabase` (contacts, photo
  index), or one of the in-memory / SharedPreferences paths already in
  use — never plain unencrypted disk.
- No telemetry, no analytics, no third-party SDKs that phone home.
  See [THREAT_MODEL.md](THREAT_MODEL.md) for what's in/out of scope.

## License headers

Every Kotlin source file in `app/src/main/java/com/privateai/camera/`
starts with two SPDX comment lines:

```kotlin
// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later
```

If you create a new file, copy that header verbatim. Do not change the
copyright line — your contribution stays under your authorship per the
CLA, but the file as part of the Project is licensed AGPL.

## Contributor License Agreement

The first time you open a pull request, the [CLA Assistant](https://cla-assistant.io/)
bot will ask you to sign the project's [Contributor License Agreement](CLA.md).
You sign once; the signature covers all of your future contributions.

The CLA preserves your authorship while giving the Project Owner the
right to relicense the codebase commercially. This is what lets Privora
remain AGPL for the public *and* ship the future closed Pro module on
the Play Store. If you're not comfortable with this arrangement, please
discuss with the maintainer before opening the PR — there are scenarios
(e.g. your employer's IP policy) where alternative arrangements can be
made.

## Reporting security issues

Don't open a public issue for security bugs. Follow the process in
[SECURITY.md](SECURITY.md) — short version: email
`robomixes2025@gmail.com` or use GitHub's private security advisories
(under the repository's Security tab).

## What kinds of contributions are welcome

- Bug fixes
- Small features with clear privacy stories
- Translations / locale corrections
- Security audits and threat-model improvements
- Documentation
- F-Droid metadata + reproducible-build manifests

Larger features and architectural changes: please open an issue first
to discuss the design before writing code. The maintainer reserves the
right to decline contributions that don't fit the project's privacy
direction or that would meaningfully complicate the codebase.

Thank you for helping make Privora better.
