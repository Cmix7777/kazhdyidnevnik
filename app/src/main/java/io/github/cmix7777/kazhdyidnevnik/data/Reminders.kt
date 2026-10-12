package io.github.cmix7777.kazhdyidnevnik.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderKind { CHECK, MORNING, BLOCK, WEATHER, EVENING, EVENT, SPECIAL }

/**
 * Одно напоминание: когда и что. Для блока учёбы [block] — его вид.
 * [partner] — напоминание про второго человека (не про владельца телефона).
 */
data class Reminder(
    val at: LocalDateTime,
    val kind: ReminderKind,
    val block: PlanKind? = null,
    val partner: Boolean = false,
    /** Для напоминания о своём деле — его номер; для особой даты — её номер. */
    val eventId: String? = null,
    /** Для особой даты: за сколько дней до неё напоминание (0 — в сам день). */
    val daysBefore: Int = 0,
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

    /** Напоминания о своих делах, которые начинаются в [date] (само напоминание может быть накануне). */
    fun forEvents(date: LocalDate, events: List<UserEvent>): List<Reminder> =
        events
            .filter { it.remindBefore != null && it.occursOn(date) }
            .map { event ->
                Reminder(
                    at = date.atTime(event.start).minusMinutes((event.remindBefore ?: 0).toLong()),
                    kind = ReminderKind.EVENT,
                    eventId = event.id,
                )
            }
            .sortedBy { it.at.toLocalTime() }

    /** Когда напоминать об особой дате: в сам день утром, а о дне рождения ещё и за неделю. */
    val specialTime: LocalTime = LocalTime.of(9, 0)
    val specialAheadTime: LocalTime = LocalTime.of(10, 0)

    /** Напоминания об особых датах (дни рождения, годовщины), которые приходятся на [date]. */
    fun forSpecial(date: LocalDate, dates: List<Deadline>): List<Reminder> {
        val result = mutableListOf<Reminder>()
        for (deadline in dates) {
            val id = deadline.id ?: continue
            if (!deadline.special) continue
            if (deadline.nextDate(date) == date) {
                result += Reminder(date.atTime(specialTime), ReminderKind.SPECIAL, eventId = id)
            }
            val ahead = SpecialDates.BIRTHDAY_AHEAD_DAYS
            if (deadline.occasion == Occasion.BIRTHDAY && deadline.nextDate(date) == date.plusDays(ahead.toLong())) {
                result += Reminder(date.atTime(specialAheadTime), ReminderKind.SPECIAL, eventId = id, daysBefore = ahead)
            }
        }
        return result
    }

    /** Насколько напоминание может опоздать (телефон спал), чтобы его ещё стоило показать. */
    fun maxDelay(kind: ReminderKind): Duration = when (kind) {
        ReminderKind.CHECK -> Duration.ofHours(6)
        ReminderKind.MORNING -> Duration.ofHours(2)
        ReminderKind.BLOCK -> Duration.ofMinutes(30)
        ReminderKind.WEATHER -> Duration.ofHours(2)
        ReminderKind.EVENING -> Duration.ofHours(2)
        ReminderKind.EVENT -> Duration.ofMinutes(30)
        ReminderKind.SPECIAL -> Duration.ofHours(10)
    }
}
