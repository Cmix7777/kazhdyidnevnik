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
