#!/usr/bin/env bash
# Builds a harmless, code-free APK for emulator profile-isolation tests.
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
fixture_dir=/tmp/cana-roadmap-fixture
mkdir -p "$fixture_dir"
cat > "$fixture_dir/AndroidManifest.xml" <<'MANIFEST'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.jordanwoodson.cana.fixture" android:versionCode="1" android:versionName="1.0">
  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35" />
  <application android:label="Cana Test Fixture" android:hasCode="false" android:allowBackup="false" />
</manifest>
MANIFEST
"$ANDROID_HOME/build-tools/36.0.0/aapt2" link -I "$ANDROID_HOME/platforms/android-36/android.jar" \
  --manifest "$fixture_dir/AndroidManifest.xml" -o "$fixture_dir/unsigned.apk"
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -f 4 "$fixture_dir/unsigned.apk" "$fixture_dir/aligned.apk"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks "$HOME/.android/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android --out "$fixture_dir/fixture.apk" "$fixture_dir/aligned.apk"
echo "$fixture_dir/fixture.apk"
