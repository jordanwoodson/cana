#!/usr/bin/env bash
# Builds two versions of an isolated, code-free icon fixture for emulator tests.
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
fixture_dir="$(mktemp -d /tmp/cana-icon-fixture.XXXXXX)"
for version in 1 2; do
  mkdir -p "$fixture_dir/$version/res/drawable"
  color="#E91E63"
  if [[ "$version" == 2 ]]; then color="#2196F3"; fi
  cat > "$fixture_dir/$version/res/drawable/fixture_icon.xml" <<ICON
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
  <path android:fillColor="$color" android:pathData="M0,0h48v48h-48z" />
</vector>
ICON
  cat > "$fixture_dir/$version/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="io.github.jordanwoodson.cana.iconfixture" android:versionCode="$version" android:versionName="$version">
  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="35" />
  <application android:label="Cana Icon Fixture" android:icon="@drawable/fixture_icon"
      android:hasCode="false" android:allowBackup="false" android:testOnly="true" />
</manifest>
MANIFEST
  "$ANDROID_HOME/build-tools/36.0.0/aapt2" compile --dir "$fixture_dir/$version/res" -o "$fixture_dir/$version/resources.zip"
  "$ANDROID_HOME/build-tools/36.0.0/aapt2" link -I "$ANDROID_HOME/platforms/android-36/android.jar" \
      --manifest "$fixture_dir/$version/AndroidManifest.xml" "$fixture_dir/$version/resources.zip" -o "$fixture_dir/$version/unsigned.apk"
  "$ANDROID_HOME/build-tools/36.0.0/zipalign" -f 4 "$fixture_dir/$version/unsigned.apk" "$fixture_dir/$version/aligned.apk"
  "$ANDROID_HOME/build-tools/36.0.0/apksigner" sign --ks "$HOME/.android/debug.keystore" \
      --ks-pass pass:android --key-pass pass:android --out "$fixture_dir/icon-v$version.apk" "$fixture_dir/$version/aligned.apk"
done
echo "$fixture_dir"
