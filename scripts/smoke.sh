#!/usr/bin/env bash
# Emulator smoke test: install the debug APK, drive the HUD like Sir would, and fail on any crash.
# Runs in CI (.github/workflows/smoke.yml). Everything it finds is printed to the job log.
set -u
PKG=com.adesh.devjarvis
APK=app/build/outputs/apk/full/debug/app-full-debug.apk
DEAD=0

adb wait-for-device
adb install -r -g "$APK" || { echo "!!! install failed"; exit 1; }
adb logcat -c
adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true
sleep 12

dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml 2>/dev/null; }
texts() { dump | tr '>' '\n' | grep -o 'text="[^"]\+"' | sed 's/^text="//; s/"$//'; }
alive() { adb shell pidof "$PKG" >/dev/null 2>&1; }
tap_node() {
  local b
  b=$(dump | tr '>' '\n' | grep -m1 -- "$1" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b
  [ $# -eq 4 ] || return 1
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
check() {
  if ! alive; then echo "!!! APP DIED after: $1"; DEAD=1; adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true; sleep 8; fi
}
type_to_jarvis() {
  echo "=== typing: $1"
  tap_node 'Type to JARVIS' || { echo "input box not found"; texts | head -20; return; }
  sleep 1
  adb shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"
  tap_node 'content-desc="Send"' || adb shell input keyevent 66
  sleep "${2:-10}"
  check "$1"
  echo "--- screen now:"; texts | tail -n 14
}

echo "=== HUD on start:"; texts | head -30
check "start"

type_to_jarvis "hello"
type_to_jarvis "hello Dev"
type_to_jarvis "torch on karo"
type_to_jarvis "volume badhao"
type_to_jarvis "YouTube kholo"
adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true; sleep 4
type_to_jarvis "5 minute ka timer lagao"
adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true; sleep 4

echo "=== always-on switch (wake engine + model download)"
tap_node 'checkable="true"' && sleep 60
check "always-on"
echo "--- screen now:"; texts | head -20
type_to_jarvis "hello" 10
type_to_jarvis "kya haal hai" 10

echo "=== talk button / assistant launch"
adb shell am start -a com.adesh.devjarvis.action.TALK -n "$PKG/.MainActivity"; sleep 12
check "talk intent"

echo "=== settings screen"
adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true; sleep 4
tap_node 'content-desc="Settings"' && sleep 4
check "settings"
texts | head -40
tap_node 'Keys check karo' && sleep 6
texts | grep -i -E "gemini|groq|eleven|brain" | head
adb shell input keyevent 4; sleep 2
for screen in Memory "Activity log"; do
  tap_node "content-desc=\"$screen\"" && sleep 3 && check "$screen" && echo "--- $screen:" && texts | head -10
  adb shell input keyevent 4; sleep 2
done

echo "=== JARVIS flight recorder"
adb shell run-as "$PKG" cat files/crash/trace.log 2>/dev/null | tail -n 60
echo "=== JARVIS problem journal"
adb shell run-as "$PKG" cat files/crash/problems.log 2>/dev/null | tail -n 40

echo "=== crashes in logcat"
adb logcat -d > logcat.txt
grep -n -E "FATAL EXCEPTION|Fatal signal|ANR in $PKG|Process: $PKG|libc  *: Fatal|terminating with uncaught" -A 30 logcat.txt | head -n 200 || true
CRASH=$(grep -c -E "FATAL EXCEPTION|Fatal signal [0-9]+ .*$PKG|ANR in $PKG" logcat.txt || true)
echo "=== result: died=$DEAD crashes_in_logcat=$CRASH"
[ "$DEAD" -eq 0 ] && [ "${CRASH:-0}" -eq 0 ]
