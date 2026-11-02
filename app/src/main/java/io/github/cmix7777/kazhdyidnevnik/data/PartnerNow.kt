package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalTime

/** Что второй человек делает сейчас — по его расписанию, для строки под именем в переписке. */
object PartnerNow {

    /** «на паре до 14:10», «на работе до 20:00», «свободна до 16:00». null — на сегодня ничего. */
    fun line(person: Person, items: List<DayItem>, now: LocalTime): String? {
        val current = items.lastOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
        if (current != null) {
            val until = time(current.end)
            return when {
                current.isWork -> "на работе до $until"
                current is DayItem.LessonItem -> "на паре до $until"
                current is DayItem.EventItem -> "${current.event.displayTitle} до $until"
                else -> null
            }
        }
        val next = items.filter { it.start.isAfter(now) }.minByOrNull { it.start } ?: return null
        val free = if (person == Person.NASTYA) "свободна" else "свободен"
        return "$free до ${time(next.start)}"
    }

    private fun time(t: LocalTime): String = "%d:%02d".format(t.hour, t.minute)
}
