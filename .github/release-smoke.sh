#!/bin/sh
# Сжатая (как у людей на телефонах) сборка на эмуляторе: ставится поверх отладочной, запускается,
# по ней случайно тыкают. Если R8 выкинул что-то нужное, здесь будет вылет.
PKG=io.github.cmix7777.kazhdyidnevnik
APK=app/build/outputs/apk/release/app-release.apk
echo "apk=$(stat -c %s $APK 2>/dev/null)"
adb uninstall $PKG >/dev/null 2>&1 || true
adb install -r $APK || { echo "release install FAILED"; exit 0; }
adb logcat -c || true
adb shell am start -W -n $PKG/.MainActivity
sleep 6
adb shell monkey -p $PKG --throttle 250 --pct-syskeys 0 --pct-appswitch 0 -s 7 600 > /dev/null 2>&1 || true
sleep 2
adb shell am start -W -n $PKG/.MainActivity > /dev/null 2>&1 || true
sleep 3
adb shell monkey -p $PKG --throttle 250 --pct-syskeys 0 --pct-appswitch 0 -s 13 600 > /dev/null 2>&1 || true
sleep 2
CRASH=$(adb logcat -d -v brief | grep -E "FATAL EXCEPTION|AndroidRuntime: (Caused by|java\.|kotlin\.|Process: $PKG)|ClassNotFound|NoSuchMethod|NoSuchField|Missing class" | head -n 30)
if [ -n "$CRASH" ]; then
  echo "release CRASH"
  echo "$CRASH"
  adb logcat -d -v brief | grep -A 25 "FATAL EXCEPTION" | head -n 60
else
  echo "release OK"
fi
