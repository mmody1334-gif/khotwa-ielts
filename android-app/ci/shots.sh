#!/bin/bash
# Runs inside the emulator step: install APK, open each screen, take screenshots.
mkdir -p shots
APK=khotwa-ielts.apk
adb install -r "$APK"
adb logcat -c
i=0
shoot() {
  i=$((i+1)); n=$(printf '%02d' $i)
  adb shell am force-stop com.khotwa.ielts
  adb shell am start -n com.khotwa.ielts/.MainActivity --es route "$1" --es lang "$2" >/dev/null
  sleep 4
  adb exec-out screencap -p > "shots/$n-$2-${1//:/_}.png"
}
for r in home program plan "ext:rmt1-1" "quiz:quick" lessons practice "paper:R:r7" "paper:L:l9" "write:a1" "speak:s1" words progress settings mocks; do shoot "$r" en; done
for r in home program plan practice settings "lesson:roadmap" "paper:R:r11" "placement"; do shoot "$r" ar; done
for k in 1 2 3 4 5; do
  i=$((i+1)); n=$(printf '%02d' $i)
  adb shell am force-stop com.khotwa.ielts
  adb shell am start -n com.khotwa.ielts/.MainActivity --es route placement --es lang en --ei plstep $k >/dev/null
  sleep 4
  adb exec-out screencap -p > "shots/$n-en-placement-$k.png"
done
# rotation test on a reading paper
adb shell am start -n com.khotwa.ielts/.MainActivity --es route "paper:R:r1" --es lang en >/dev/null; sleep 3
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1; sleep 3
adb exec-out screencap -p > shots/99-rotated.png
adb shell settings put system user_rotation 0
adb logcat -d -s AndroidRuntime:E > shots/crash.txt
cat shots/crash.txt | head -60
echo "shots done"
