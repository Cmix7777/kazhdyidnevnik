package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.formatTime
import java.time.LocalDate

/** Тексты уведомлений: утренняя сводка, блок учёбы, вечерняя проверка. */
object Summaries {

    const val MORNING_TITLE = "Доброе утро, господин Айзат"

    /**
     * Утренняя сводка. [items] — пары и смены дня или null, если расписание не загружено.
     * [forecast] — прогноз погоды, если удалось его получить.
     */
    fun morning(
        date: LocalDate,
        items: List<DayItem>?,
        plan: List<PlanBlock>,
        forecast: Forecast? = null,
    ): String {
        val lines = mutableListOf<String>()

        if (items == null) {
            lines += "Расписание пар не загружено, открой приложение."
        } else {
            val lessons = items.filterIsInstance<DayItem.LessonItem>().map { it.lesson }
            lines += if (lessons.isEmpty()) {
                "Пар нет."
            } else {
                val first = lessons.first()
                val where = when {
                    first.online -> ", онлайн"
                    first.room.isBlank() -> ""
                    first.room.first().isDigit() -> ", ауд. ${first.room}"
                    else -> ", ${first.room}"
                }
                "${pluralLessons(lessons.size)}, первая в ${formatTime(first.start)}$where."
            }
            items.filterIsInstance<DayItem.WorkItem>().forEach {
                lines += "Работа ${formatTime(it.start)}–${formatTime(it.end)}."
            }
        }

        lines += WeatherAdvice.morningLines(date, items, forecast)

        Practice.dayNumber(date)?.let { lines += "Практика: день $it из ${Practice.totalDays}." }

        plan.firstOrNull { it.kind == PlanKind.MOCK }?.let {
            lines += "Сегодня пробный демоэкзамен: ${formatTime(it.start)}–${formatTime(it.end)}."
        }
        if (plan.isNotEmpty()) {
            lines += "Учёба: ${formatMinutes(plan.sumOf { it.minutes })}, первый блок в ${formatTime(plan.first().start)}."
        }

        Deadlines.default
            .map { it to daysBetween(date, it.date) }
            .filter { (_, days) -> days in 0L..7L }
            .forEach { (deadline, days) ->
                val approx = if (deadline.approximate != null) " (дата примерная)" else ""
                lines += "${deadline.title}: ${countdownText(days)}$approx."
            }

        return lines.joinToString("\n")
    }

    /** Текст уведомления о начале блока учёбы. */
    fun block(block: PlanBlock): String =
        "${formatTime(block.start)}–${formatTime(block.end)} · ${formatMinutes(block.minutes)}\n${block.detail}"

    /** Вечернее напоминание или null, если всё отмечено (или плана не было). */
    fun evening(date: LocalDate, plan: List<PlanBlock>, done: Map<String, Int>): String? {
        if (plan.isEmpty()) return null
        val left = plan.filter { it.key(date) !in done }
        if (left.isEmpty()) return null
        val lines = mutableListOf(
            "Отмечено ${plan.size - left.size} из ${plan.size}. Отметь в приложении, что успел сделать.",
        )
        if (left.any { it.kind == PlanKind.PDD }) {
            val streak = ProgressStats.pddStreak(done, date)
            lines += if (streak > 0) {
                "Билеты ПДД сегодня ещё не решены, серия ${pluralDays(streak.toLong())} прервётся."
            } else {
                "Билеты ПДД сегодня ещё не решены."
            }
        }
        return lines.joinToString("\n")
    }
}
