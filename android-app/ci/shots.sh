#!/bin/bash
# Runs inside the emulator step: install APK, open each screen, take screenshots.
mkdir -p shots
APK=android-app/app/build/outputs/apk/debug/app-debug.apk
adb install -r "$APK"
adb logcat -c
i=0
for r in home plan "quiz:quick" lessons practice "paper:R:r1" "paper:L:l1" "write:a1" "speak:s1" words progress about settings mocks results; do
  i=$((i+1))
  n=$(printf '%02d' $i)
  adb shell am force-stop com.khotwa.ielts
  adb shell am start -n com.khotwa.ielts/.MainActivity --es route "$r" >/dev/null
  sleep 4
  adb exec-out screencap -p > "shots/$n-${r//:/_}.png"
done
adb logcat -d -s AndroidRuntime:E > shots/crash.txt
cat shots/crash.txt | head -40
