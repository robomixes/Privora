# Privacy Policy — Privo

**Last updated**: April 2026

## Summary

Privo is designed to protect your privacy. All AI processing runs on your device. Your photos, videos, and notes are encrypted and never leave your phone. We collect no data, no analytics, and no telemetry.

## Data Collection

**We collect nothing.** No personal data, no usage data, no crash reports, no device identifiers.

## Data Storage

- All photos, videos, and notes are encrypted with AES-256-GCM using a per-install key stored in your phone's hardware security module (TEE/StrongBox)
- Encrypted data is stored only in the app's private directory on your device
- Data is excluded from device backups (`android:allowBackup="false"`)
- Only you can access your data through biometric authentication or your PIN

## Network Usage

| What | Network? | Details |
|------|----------|---------|
| AI object detection | No | YOLOv8n model bundled in APK, runs on-device |
| Document scanning | No* | ML Kit Document Scanner runs locally |
| OCR text recognition | No* | ML Kit Text Recognition runs locally |
| QR/Barcode scanning | No* | ML Kit Barcode Scanning runs locally |
| Translation | Partial* | ML Kit downloads language models once, then runs offline |
| Face detection | No* | ML Kit Face Detection runs locally |
| Analytics/telemetry | No | None. No Firebase, no Crashlytics, no tracking SDKs |

*ML Kit components run through Google Play Services on your device. Google's ML Kit privacy policy applies to the processing pipeline. We plan to replace ML Kit with fully on-device alternatives in a future update.

## Third-Party Services

- **Google ML Kit** (via Google Play Services): Used for document scanning, OCR, barcode scanning, translation, and face detection. ML Kit processes data on-device through Play Services. See [Google's ML Kit Terms](https://developers.google.com/ml-kit/terms).
- **ONNX Runtime**: Open-source AI inference engine. Runs entirely on-device. No network access.

## Encryption

- Algorithm: AES-256-GCM
- Key management: Two-layer architecture (KEK in Android Keystore hardware + DEK for data)
- EXIF metadata (GPS, device info, timestamps) is stripped from all shared images
- Face blur option available when sharing photos

## Data Export & Backup

- You can export an encrypted backup (.paicbackup file) protected by a password you choose
- The backup contains your encryption key (wrapped with your password) and your encrypted files
- Only you can decrypt the backup with your password
- We have no access to your backup password or encryption keys

## Emergency PIN

- Optional feature that shows an empty vault when a special PIN is entered
- In wipe mode, the encryption key is permanently destroyed, making all data unreadable
- No trace of the emergency PIN feature remains after activation

## Children's Privacy

This app does not knowingly collect any data from children under 13.

## Changes to This Policy

We may update this policy. Changes will be reflected in the "Last updated" date above.

## Contact

For questions about this privacy policy, open an issue at:
https://github.com/robomixes/private-ai-camera/issues
