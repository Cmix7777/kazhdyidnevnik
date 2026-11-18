package io.github.cmix7777.kazhdyidnevnik.data

import java.time.LocalDate

/** Откуда берётся расписание одного человека и где лежит его копия на телефоне. */
interface ScheduleSource {

    /** Скачать неделю, не сохраняя: 0 — текущая, 1 — следующая, -1 — прошлая. */
    suspend fun download(skip: Int, today: LocalDate = LocalDate.now(), timeoutMs: Int = 20_000): WeekSchedule

    /** Сохранённая копия недели (для сравнения с новой). */
    fun loadCached(weekStart: LocalDate): WeekSchedule?

    /** Что показать: сохранённая неделя, а если её нет — собранная из того, что уже скачано. */
    fun loadOrBuild(weekStart: LocalDate): WeekSchedule? = loadCached(weekStart)

    fun save(week: WeekSchedule)
}

/** Расписание без пар в дни практики: колледж может держать на сайте обычную сетку, а пар в эти дни нет. */
class PracticeFilteredSource(private val inner: ScheduleSource, private val period: PracticePeriod) : ScheduleSource {

    override suspend fun download(skip: Int, today: LocalDate, timeoutMs: Int): WeekSchedule =
        strip(inner.download(skip, today, timeoutMs))

    override fun loadCached(weekStart: LocalDate): WeekSchedule? = inner.loadCached(weekStart)?.let(::strip)

    override fun loadOrBuild(weekStart: LocalDate): WeekSchedule? = inner.loadOrBuild(weekStart)?.let(::strip)

    override fun save(week: WeekSchedule) = inner.save(strip(week))

    private fun strip(week: WeekSchedule): WeekSchedule = week.copy(lessons = week.lessons.filterNot { period.contains(it.date) })
}
