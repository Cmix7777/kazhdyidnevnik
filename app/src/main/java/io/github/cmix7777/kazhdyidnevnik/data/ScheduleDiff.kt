package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.formatShortDay
import io.github.cmix7777.kazhdyidnevnik.formatTime
import java.time.LocalDate
import java.time.LocalTime

/** Что поменялось в расписании одной пары. */
sealed interface ScheduleChange {
    val lesson: Lesson

    /** Пара появилась. */
    data class Added(override val lesson: Lesson) : ScheduleChange

    /** Пару убрали. */
    data class Removed(override val lesson: Lesson) : ScheduleChange

    /** У пары сменились предмет, время или аудитория. [lesson] — новая версия. */
    data class Changed(val old: Lesson, override val lesson: Lesson) : ScheduleChange
}

/** Сравнивает старое и новое расписание недели. */
object ScheduleDiff {

    /** Изменения начиная с дня [from]: прошедшие дни не интересны. Тема пары и преподаватель не учитываются. */
    fun diff(old: List<Lesson>, new: List<Lesson>, from: LocalDate): List<ScheduleChange> {
        val oldByKey = keyed(old.filter { !it.date.isBefore(from) })
        val newByKey = keyed(new.filter { !it.date.isBefore(from) })
        val changes = (oldByKey.keys + newByKey.keys).mapNotNull { key ->
            val before = oldByKey[key]
            val after = newByKey[key]
            when {
                before == null && after != null -> ScheduleChange.Added(after)
                before != null && after == null -> ScheduleChange.Removed(before)
                before != null && after != null && significant(before) != significant(after) ->
                    ScheduleChange.Changed(before, after)
                else -> null
            }
        }
        return changes.sortedWith(compareBy({ it.lesson.date.toEpochDay() }, { it.lesson.start }))
    }

    /** Одна строка для уведомления. */
    fun describe(change: ScheduleChange): String {
        val lesson = change.lesson
        val day = formatShortDay(lesson.date)
        return when (change) {
            is ScheduleChange.Added -> {
                val room = roomLabel(lesson)?.let { ", $it" }.orEmpty()
                "$day: добавлена ${pairLabel(lesson)} — ${lesson.subject}$room"
            }
            is ScheduleChange.Removed -> "$day: отменена ${pairLabel(lesson)} — ${lesson.subject}"
            is ScheduleChange.Changed -> {
                val old = change.old
                val parts = buildList {
                    if (old.subject != lesson.subject) add("${old.subject} → ${lesson.subject}")
                    if (old.start != lesson.start || old.end != lesson.end) {
                        add("${range(old)} → ${range(lesson)}")
                    }
                    if (old.room != lesson.room) {
                        add("${roomLabel(old) ?: "без аудитории"} → ${roomLabel(lesson) ?: "без аудитории"}")
                    }
                }
                val name = lesson.number?.let { "$it пара" } ?: "пара в ${formatTime(old.start)}"
                val subject = if (old.subject == lesson.subject) ", ${lesson.subject}" else ""
                "$day, $name$subject: " + parts.joinToString(", ")
            }
        }
    }

    private data class Significant(val subject: String, val start: LocalTime, val end: LocalTime, val room: String)

    private fun significant(lesson: Lesson) = Significant(lesson.subject, lesson.start, lesson.end, lesson.room)

    /**
     * Ключ пары — день и номер (или время, если номера нет). Если у двух пар один номер
     * (например, подгруппы), они различаются порядковым индексом.
     */
    private fun keyed(lessons: List<Lesson>): Map<String, Lesson> {
        val result = mutableMapOf<String, Lesson>()
        lessons.groupBy { "${it.date}|${it.number ?: it.start}" }.forEach { (slot, group) ->
            group.sortedWith(compareBy({ it.subject }, { it.room })).forEachIndexed { index, lesson ->
                result["$slot|$index"] = lesson
            }
        }
        return result
    }

    private fun pairLabel(lesson: Lesson): String =
        (lesson.number?.let { "$it пара" } ?: "пара") + " в ${formatTime(lesson.start)}"

    private fun range(lesson: Lesson) = "${formatTime(lesson.start)}–${formatTime(lesson.end)}"

    private fun roomLabel(lesson: Lesson): String? = when {
        lesson.online -> "онлайн"
        lesson.room.isBlank() -> null
        lesson.room.first().isDigit() -> "ауд. ${lesson.room}"
        else -> lesson.room
    }
}
