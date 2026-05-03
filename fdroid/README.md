# F-Droid submission files

This directory holds files used by the F-Droid build/distribution pipeline.
They are **not** consumed by Privora's own Gradle build — they're staging
copies that get sent to F-Droid's separate metadata repository.

## Files

- **`com.privateai.camera.yml`** — F-Droid build manifest. Lists source
  repo, license, build config, anti-features, and the per-version
  reproducible-build entries. Submitted as a merge request to
  [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) (separate repo
  maintained by F-Droid).

## Submission checklist

Before opening the fdroiddata MR:

1. Confirm the latest tag in this repo (`git tag --sort=-v:refname | head`)
   matches the `commit:` field in the YAML's most recent `Builds:` entry.
2. Run `./gradlew assembleFdroidRelease` locally — output APK in
   `app/build/outputs/apk/fdroid/release/` should be byte-identical to
   what F-Droid's build server will produce (modulo signing).
3. Verify `LICENSE`, `README.md`, `CHANGELOG.md`, `SECURITY.md`, and
   `THREAT_MODEL.md` are present at repo root and current.
4. Tag the commit (`git tag v2.0.5 && git push --tags`) so F-Droid's
   `UpdateCheckMode: Tags` can find it.

## Submission steps

```bash
# Fork https://gitlab.com/fdroid/fdroiddata on GitLab, then:
git clone https://gitlab.com/<your-gitlab-user>/fdroiddata.git
cd fdroiddata
cp /path/to/Privora/fdroid/com.privateai.camera.yml metadata/
git add metadata/com.privateai.camera.yml
git commit -m "Add Privora"
git push origin main

# Then open a merge request against fdroiddata/main on GitLab.
# F-Droid maintainers will review, build, and publish.
```

First-time review typically takes 1–3 weeks. Subsequent version updates
are usually picked up automatically thanks to `UpdateCheckMode: Tags`.

## NonFreeNet anti-feature

Privora is flagged with `NonFreeNet` because the AI Assistant feature
downloads a ~2.5 GB Gemma 4 model file from Hugging Face on first enable.
This is a one-shot, user-initiated fetch; all subsequent inference runs
entirely on-device. Users who don't enable AI never trigger any network
activity from this code path.
