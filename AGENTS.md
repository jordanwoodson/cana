# Working on Cana

- Work on `master` and push it. There are no other branches.
- Every push to `master` that changes the app (`app/`, Gradle files) builds a signed APK and publishes
  it as a GitHub release, which Obtainium on the phone installs as an update
  (`.github/workflows/release.yml`).
- Don't bump `version_name`/`version_code`, create tags or run `gh release` by hand. CI assigns the
  next `3.2.2-cana.N`.
- Release notes are the commit subjects since the last release, so write subjects a user can read.
- Never change the release signing key: Android only installs updates signed with the same certificate.
- Check before pushing: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug`
