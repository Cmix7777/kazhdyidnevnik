package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalTime

/**
 * Живая полоска дня на экране «Сегодня»: пары, работа и дела отрезками на одной линии,
 * бусина «сейчас» и короткая подпись, что идёт и что дальше.
 */
data class DayStrip(
    val from: LocalTime,
    val to: LocalTime,
    val blocks: List<Block>,
    /** Где сейчас на полоске, от 0 до 1 (до начала дня — 0). */
    val now: Float,
    /** «сейчас Математика, ещё 40 мин», «дальше работа через 25 мин». */
    val info: String,
) {
    enum class Kind { LESSON, WORK, EVENT }

    /** Отрезок на полоске: [start] и [end] — доли от 0 до 1. */
    data class Block(val start: Float, val end: Float, val kind: Kind, val title: String)

    /** Сейчас идёт какой-то отрезок. */
    val busyNow: Boolean get() = blocks.any { now >= it.start && now < it.end }

    companion object {
        /**
         * Полоска на сегодня или null, если показывать нечего: дел нет или день уже закончился
         * (тогда хватает карточек, полоска только занимала бы место).
         */
        fun build(items: List<DayItem>, now: LocalTime): DayStrip? {
            if (items.isEmpty()) return null
            val from = items.minOf { it.start }
            val to = items.maxOf { it.end }
            val length = (to.toSecondOfDay() - from.toSecondOfDay()).toFloat()
            if (length <= 0f || !now.isBefore(to)) return null
            fun at(time: LocalTime) = ((time.toSecondOfDay() - from.toSecondOfDay()) / length).coerceIn(0f, 1f)
            val blocks = items.map { Block(at(it.start), at(it.end), kindOf(it), titleOf(it)) }
            return DayStrip(from, to, blocks, at(now), info(items, now))
        }

        private fun kindOf(item: DayItem): Kind = when {
            item.isWork -> Kind.WORK
            item is DayItem.LessonItem -> Kind.LESSON
            else -> Kind.EVENT
        }

        private fun titleOf(item: DayItem): String = when {
            item.isWork -> "работа"
            item is DayItem.LessonItem -> item.lesson.subject
            item is DayItem.EventItem -> item.event.displayTitle
            else -> ""
        }

        private fun minutesBetween(a: LocalTime, b: LocalTime): Int =
            ((b.toSecondOfDay() - a.toSecondOfDay() + 59) / 60).coerceAtLeast(1)

        private fun info(items: List<DayItem>, now: LocalTime): String {
            val current = items.lastOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
            if (current != null) {
                return "сейчас ${titleOf(current)}, ещё ${formatMinutes(minutesBetween(now, current.end))}"
            }
            val next = items.firstOrNull { it.start.isAfter(now) } ?: return "на сегодня всё"
            return "дальше ${titleOf(next)} через ${formatMinutes(minutesBetween(now, next.start))}"
        }
    }
}
