package io.github.cmix7777.kazhdyidnevnik.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind { CHECK, MORNING, BLOCK, WEATHER, EVENING }

/**
 * Одно напоминание: когда и что. Для блока учёбы [block] — его вид.
 * [partner] — напоминание про второго человека (не про владельца телефона).
 */
data class Reminder(
    val at: LocalDateTime,
    val kind: ReminderKind,
    val block: PlanKind? = null,
    val partner: Boolean = false,
)

/** Какие уведомления приходят про второго человека. По умолчанию — никакие. */
data class PartnerAlerts(
    val changes: Boolean = false,
    val morning: Boolean = false,
    val study: Boolean = false,
    val weather: Boolean = false,
)

/**
 * Какие уведомления включены. Поля верхнего уровня — про владельца телефона,
 * [partner] — про второго человека.
 */
data class ReminderSettings(
    val changes: Boolean = true,
    val morning: Boolean = true,
    val morningTime: LocalTime = LocalTime.of(7, 45),
    val blocks: Boolean = true,
    val evening: Boolean = true,
    val eveningTime: LocalTime = LocalTime.of(22, 15),
    val weather: Boolean = true,
    val partner: PartnerAlerts = PartnerAlerts(),
)

object ReminderPlanner {

    /** Когда проверять сайт расписания: утром до выхода и поздно вечером. */
    val checkTimes: List<LocalTime> = listOf(LocalTime.of(6, 30), LocalTime.of(23, 0))

    /** Когда присылать погоду на завтра. */
    val weatherTime: LocalTime = LocalTime.of(21, 0)

    /** Напоминания на один день. [plan] — план учёбы этого дня. */
    fun forDay(date: LocalDate, plan: List<PlanBlock>, settings: ReminderSettings): List<Reminder> {
        val result = mutableListOf<Reminder>()
        if (settings.changes || settings.partner.changes) {
            checkTimes.forEach { result += Reminder(date.atTime(it), ReminderKind.CHECK) }
        }
        if (settings.morning) {
            result += Reminder(date.atTime(settings.morningTime), ReminderKind.MORNING)
        }
        if (settings.blocks) {
            plan.forEach { result += Reminder(date.atTime(it.start), ReminderKind.BLOCK, it.kind) }
        }
        if (settings.weather) {
            result += Reminder(date.atTime(weatherTime), ReminderKind.WEATHER)
        }
        if (settings.evening && plan.isNotEmpty()) {
            // Вечером, но не раньше, чем закончится последний блок.
            val afterLast = plan.maxOf { it.end }.plusMinutes(5)
            result += Reminder(date.atTime(maxOf(settings.eveningTime, afterLast)), ReminderKind.EVENING)
        }
        return result.sortedBy { it.at.toLocalTime() }
    }

    /** Напоминания про второго человека: его утро, блоки учёбы и погода. Без вечерней отметки. */
    fun forPartnerDay(date: LocalDate, plan: List<PlanBlock>, settings: ReminderSettings): List<Reminder> {
        val alerts = settings.partner
        val result = mutableListOf<Reminder>()
        if (alerts.morning) {
            result += Reminder(date.atTime(settings.morningTime), ReminderKind.MORNING, partner = true)
        }
        if (alerts.study) {
            plan.forEach { result += Reminder(date.atTime(it.start), ReminderKind.BLOCK, it.kind, partner = true) }
        }
        if (alerts.weather) {
            result += Reminder(date.atTime(weatherTime), ReminderKind.WEATHER, partner = true)
        }
        return result.sortedBy { it.at.toLocalTime() }
    }

    /** Насколько напоминание может опоздать (телефон спал), чтобы его ещё стоило показать. */
    fun maxDelay(kind: ReminderKind): Duration = when (kind) {
        ReminderKind.CHECK -> Duration.ofHours(6)
        ReminderKind.MORNING -> Duration.ofHours(2)
        ReminderKind.BLOCK -> Duration.ofMinutes(30)
        ReminderKind.WEATHER -> Duration.ofHours(2)
        ReminderKind.EVENING -> Duration.ofHours(2)
    }
}
