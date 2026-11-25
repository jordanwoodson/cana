#!/usr/bin/env bash
# Run against a fresh, English-language Android emulator with the debug APKs already built.
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
adb=("$ANDROID_HOME/platform-tools/adb" -s "${ANDROID_SERIAL:-emulator-5554}")
[[ "$("${adb[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
workdir=$(mktemp -d)
trap 'rm -rf -- "$workdir"' EXIT

curl --fail --silent --show-error --location --retry 3 \
  https://github.com/thedjchi/Shizuku/releases/download/v13.7.0-thedjchi/shizuku-v13.7.0-thedjchi.apk -o "$workdir/shizuku.apk"
printf '6ea6dee65d5ddc626b6b75b2c2f67f8cc547fa47d7b437e6892639c37eaffe43  %s\n' "$workdir/shizuku.apk" | sha256sum -c -
fixture=$(CANA_FIXTURE_DIR="$workdir/fixture" bash scripts/create-test-fixture.sh)
for apk in "$fixture" app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$workdir/shizuku.apk"; do
  "${adb[@]}" install -r -t --user 0 "$apk"
done
work_user=$("${adb[@]}" shell pm list users | sed -n 's/.*UserInfo{\([0-9]*\):CanaReleaseWork:.*/\1/p')
if [[ -z "$work_user" ]]; then
  created=$("${adb[@]}" shell pm create-user --profileOf 0 --managed CanaReleaseWork | tr -d '\r')
  [[ "$created" =~ ^Success:.*[[:space:]]([0-9]+)$ ]]
  work_user=${BASH_REMATCH[1]}
fi
"${adb[@]}" shell am start-user "$work_user"
for attempt in {1..60}; do
  state=$("${adb[@]}" shell am get-started-user-state "$work_user" | tr -d '\r')
  if [[ "$state" == RUNNING_UNLOCKED ]]; then break; fi
  sleep 1
done
[[ "$state" == RUNNING_UNLOCKED ]]
for pkg in io.github.jordanwoodson.cana io.github.jordanwoodson.cana.test moe.shizuku.privileged.api; do
  "${adb[@]}" shell cmd package install-existing --user "$work_user" "$pkg"
done
manager=$("${adb[@]}" shell pm path --user 0 moe.shizuku.privileged.api | sed -n 's/^package://p' | tr -d '\r')
"${adb[@]}" shell "${manager%/*}/lib/x86_64/libshizuku.so"
"${adb[@]}" shell wm dismiss-keyguard
run_test() {
  "${adb[@]}" shell am instrument --user "$work_user" -w -e workUser "$work_user" -e class "$1" \
    io.github.jordanwoodson.cana.test/androidx.test.runner.AndroidJUnitRunner | tee "$workdir/result"
  if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$workdir/result"; then
    "${adb[@]}" logcat -d -t 8000 -s PackageOps ActivityManager:W BroadcastQueue:W PackageManager:W | tail -120
    return 1
  fi
}
run_test io.github.samolego.canta.ops.WorkProfileAuthorizationTest
run_test 'io.github.samolego.canta.ops.FoundationDeviceTest#userAppRemovalIsScopedToPersonalAndWorkProfiles'
if [[ "$("${adb[@]}" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]]; then
  run_test 'io.github.samolego.canta.ops.FoundationDeviceTest#interruptedPrivacyRecordIsSettledFromTheSelectedProfileWithoutReplaying'
  run_test 'io.github.samolego.canta.ops.FoundationDeviceTest#changedKeyboardRequiresNewApprovalBeforeMutation'
  run_test io.github.samolego.canta.ui.PackageCsvExportDeviceTest
  run_test io.github.samolego.canta.ops.SafetyDeviceTest
  run_test io.github.samolego.canta.ops.ActionCapabilityDeviceTest
  run_test 'io.github.samolego.canta.ops.PrivacyDeviceTest#typedPrivacyActionsAndUndoPreservePeerAndCapturedValues'
fi
