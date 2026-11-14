#!/usr/bin/env bash
# Emulator smoke test: install the debug APK, drive the HUD like Sir would, and fail on any crash.
# Runs in CI (.github/workflows/smoke.yml). Everything it finds is printed to the job log.
set -u
PKG=com.adesh.devjarvis
APK=app/build/outputs/apk/full/debug/app-full-debug.apk
DEAD=0
TYPED=0

adb wait-for-device
# A freshly booted CI emulator is slow: let it settle so the launcher doesn't show "isn't responding".
sleep 45
adb install -r -g "$APK" || { echo "!!! install failed"; exit 1; }
adb logcat -c
# The CI emulator's launcher keeps hanging ("Pixel Launcher isn't responding"); JARVIS doesn't need it.
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true
sleep 12

# Screenshots of every screen, published to the ci-screens branch by the workflow (for design review).
mkdir -p screens
shot() { adb exec-out screencap -p > "screens/$1.png" 2>/dev/null && echo "(screenshot $1)"; }
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
# Close any "<app> isn't responding" dialog (it covers the whole screen on a slow emulator).
dismiss() {
  local i
  for i in 1 2 3; do
    if dump | grep -q "isn't responding\|isn&apos;t responding"; then
      echo "(closing a system 'not responding' dialog)"
      tap_node 'text="Wait"' || adb shell input keyevent 4
      sleep 3
    else
      return 0
    fi
  done
}
check() {
  if ! alive; then echo "!!! APP DIED after: $1"; DEAD=1; adb shell am start -n "$PKG/.MainActivity" --ez smoke_skip_setup true; sleep 8; fi
}
# Types to JARVIS through the debug-only intent extra: no screen scraping, so a slow or hung launcher can't block it.
type_to_jarvis() {
  echo "=== typing: $1"
  local before
  before=$(adb shell run-as "$PKG" cat files/crash/trace.log 2>/dev/null | wc -l)
  adb shell "am start -n $PKG/.MainActivity --ez smoke_skip_setup true --es smoke_say '$1'" >/dev/null
  TYPED=$((TYPED + 1))
  sleep "${2:-10}"
  check "$1"
  echo "--- JARVIS did:"
  adb shell run-as "$PKG" cat files/crash/trace.log 2>/dev/null | tail -n +"$((before + 1))" | head -n 12
}

dismiss
sleep 3; shot 01_home_start
echo "=== HUD on start:"; texts | head -30
check "start"

type_to_jarvis "hello"
type_to_jarvis "hello Dev"
type_to_jarvis "torch on karo"
type_to_jarvis "volume badhao"
type_to_jarvis "YouTube kholo"
type_to_jarvis "5 minute ka timer lagao"
type_to_jarvis "brightness kam karo"
type_to_jarvis "wifi band karo"
type_to_jarvis "EMI kitni banegi 5 lakh 10 percent 3 saal"

shot 02_home_chat
type_to_jarvis "gf ban ke baat karo" 8
shot 03_home_gf
type_to_jarvis "dost ban jao" 8
shot 04_home_dost
type_to_jarvis "Jarvis ban jao" 8

echo "=== always-on switch (wake engine + model download)"
dismiss
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
dismiss
tap_node 'content-desc="Settings"' && sleep 4
check "settings"
shot 05_settings_top
adb shell input swipe 540 1900 540 500 400; sleep 2; shot 06_settings_2
adb shell input swipe 540 1900 540 500 400; sleep 2; shot 07_settings_3
adb shell input swipe 540 1900 540 500 400; sleep 2; shot 08_settings_4
adb shell input swipe 540 500 540 2200 200; adb shell input swipe 540 500 540 2200 200; adb shell input swipe 540 500 540 2200 200; sleep 2
texts | head -40
tap_node 'Keys check karo' && sleep 6
texts | grep -i -E "gemini|groq|eleven|brain" | head
adb shell input keyevent 4; sleep 2
for screen in Memory "Activity log"; do
  tap_node "content-desc=\"$screen\"" && sleep 3 && check "$screen" && shot "09_$(echo "$screen" | tr ' ' '_')" && echo "--- $screen:" && texts | head -10
  adb shell input keyevent 4; sleep 2
done

echo "=== JARVIS flight recorder"
adb shell run-as "$PKG" cat files/crash/trace.log 2>/dev/null | tail -n 60
echo "=== JARVIS problem journal"
adb shell run-as "$PKG" cat files/crash/problems.log 2>/dev/null | tail -n 40

echo "=== crashes in logcat"
adb logcat -d > logcat.txt
# Only JARVIS's own crashes count (the emulator's uiautomator and launcher crash on their own).
grep -n -E "Process: $PKG|ANR in $PKG|>>> $PKG <<<|Fatal signal [0-9]+ .*$PKG" -B 2 -A 30 logcat.txt | head -n 200 || true
CRASH=$(grep -c -E "Process: $PKG|ANR in $PKG|>>> $PKG <<<|Fatal signal [0-9]+ .*$PKG" logcat.txt || true)
echo "=== result: died=$DEAD crashes_in_logcat=$CRASH typed=$TYPED"
[ "$DEAD" -eq 0 ] && [ "${CRASH:-0}" -eq 0 ] && [ "$TYPED" -gt 0 ]
