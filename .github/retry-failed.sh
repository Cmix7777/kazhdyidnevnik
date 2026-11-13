#!/bin/sh
# Повтор упавших тестов по одному. На медленном эмуляторе бывают случайные сбои: зависший рабочий стол
# забирает фокус (и Android не даёт читать буфер обмена), сообщение не успевает за 20 секунд.
# Если каждый упавший тест при повторе проходит, в [вывод] пишется RETRY OK. Настоящая ошибка падает и второй раз.
LOG="$1"
OUT="$2"
: > "$OUT"
grep -q "FAILURES" "$LOG" 2>/dev/null || exit 0
grep -oE '^[0-9]+\) [A-Za-z0-9_]+\([A-Za-z0-9_.]+\)' "$LOG" | sed -E 's/^[0-9]+\) ([A-Za-z0-9_]+)\(([A-Za-z0-9_.]+)\)/\2#\1/' | sort -u > failed.txt
n=$(wc -l < failed.txt | tr -d ' ')
total=$(grep -oE 'Failures: +[0-9]+' "$LOG" | tail -n 1 | grep -oE '[0-9]+')
if [ "$n" = 0 ] || [ "$n" != "$total" ]; then echo "упавшие не разобрать ($n из $total), без повтора" >> "$OUT"; exit 0; fi
if [ "$n" -gt 3 ]; then echo "упало много ($n), без повтора" >> "$OUT"; exit 0; fi
ok=1
while read -r t; do
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS < /dev/null > /dev/null 2>&1
  adb shell input keyevent KEYCODE_HOME < /dev/null > /dev/null 2>&1
  sleep 3
  adb shell am instrument -w -e class "$t" io.github.cmix7777.kazhdyidnevnik.test/androidx.test.runner.AndroidJUnitRunner < /dev/null > one.log 2>&1
  if grep -q "^OK (" one.log; then
    echo "повтор прошёл: $t" >> "$OUT"
  else
    echo "повтор упал: $t" >> "$OUT"
    grep -v "^\s*at " one.log | tail -n 25 >> "$OUT"
    ok=0
  fi
done < failed.txt
if [ "$ok" = 1 ]; then echo "RETRY OK" >> "$OUT"; fi
exit 0
