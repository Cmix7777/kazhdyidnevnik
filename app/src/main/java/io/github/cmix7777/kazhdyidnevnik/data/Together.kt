package io.github.cmix7777.kazhdyidnevnik.data

/**
 * Расчёты для совместной недели: чем занят день и когда свободны оба.
 * Время считается в минутах от полуночи, день на общей неделе идёт с 8:00 до 23:00.
 */
object TogetherDay {
    const val START = 8 * 60
    const val END = 23 * 60

    /** Окно «оба свободны» показывается, только если оно не короче двух часов. */
    const val MIN_FREE = 120

    /** Начало и конец дела в минутах. Дело через полночь в свой первый день идёт до конца суток. */
    fun minutes(item: DayItem): Pair<Int, Int> = item.start.toSecondOfDay() / 60 to item.end.toSecondOfDay() / 60

    /** Промежутки между [start] и [end], когда у обоих нет ни пар, ни работы, ни дел, не короче [minMinutes]. */
    fun freeTogether(
        a: List<DayItem>,
        b: List<DayItem>,
        start: Int = START,
        end: Int = END,
        minMinutes: Int = MIN_FREE,
    ): List<Pair<Int, Int>> {
        val busy = (a + b).map(::minutes).filter { it.second > it.first }.sortedBy { it.first }
        val free = mutableListOf<Pair<Int, Int>>()
        var cursor = start
        for ((from, to) in busy) {
            if (cursor >= end) break
            if (from > cursor) free += cursor to minOf(from, end)
            cursor = maxOf(cursor, to)
        }
        if (cursor < end) free += cursor to end
        return free.filter { it.second - it.first >= minMinutes }
    }

    /** Чем занят день, без повторов: «пары + работа». */
    fun kinds(items: List<DayItem>): String = items.map(::kind).distinct().joinToString(" + ")

    fun kind(item: DayItem): String = when (item) {
        is DayItem.LessonItem -> "пары"
        is DayItem.WorkItem -> "работа"
        is DayItem.EventItem ->
            if (item.event.kind == EventKind.OTHER) item.event.displayTitle.lowercase() else item.event.kind.title.lowercase()
    }
}
