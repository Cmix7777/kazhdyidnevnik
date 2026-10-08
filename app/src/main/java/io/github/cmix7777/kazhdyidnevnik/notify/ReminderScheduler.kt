package io.github.cmix7777.kazhdyidnevnik.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.PlanGenerator
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.data.Reminder
import io.github.cmix7777.kazhdyidnevnik.data.ReminderKind
import io.github.cmix7777.kazhdyidnevnik.data.ReminderPlanner
import io.github.cmix7777.kazhdyidnevnik.data.Summaries
import io.github.cmix7777.kazhdyidnevnik.data.WeatherAdvice
import io.github.cmix7777.kazhdyidnevnik.data.buildDay
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.service.WeatherRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Ставит один будильник на ближайшее напоминание. Когда он срабатывает,
 * показывает всё, что пора, и ставит следующий.
 */
object ReminderScheduler {

    const val ACTION_ALARM = "io.github.cmix7777.kazhdyidnevnik.ALARM"

    /** Пары и смены дня человека из сохранённого расписания или null, если неделя не загружена. */
    fun dayItems(context: Context, person: Person, date: LocalDate): List<DayItem>? =
        ScheduleSync.repository(context, person).loadOrBuild(weekStartFor(date))
            ?.let { buildDay(date, it.lessons, person.profile.shifts) }

    /** План учёбы человека на день (у кого плана нет — пусто). */
    fun plan(context: Context, person: Person, date: LocalDate): List<PlanBlock> {
        if (!person.profile.hasPlan) return emptyList()
        return dayItems(context, person, date)?.let { PlanGenerator.planFor(date, it) }.orEmpty()
    }

    /** Напоминания в промежутке (from, until]: свои и, если включены, про второго человека. */
    private fun reminders(context: Context, from: LocalDateTime, until: LocalDateTime): List<Reminder> {
        val appSettings = AppSettings(context)
        val owner = appSettings.owner ?: return emptyList()
        val settings = appSettings.reminders
        val result = mutableListOf<Reminder>()
        var date = from.toLocalDate()
        while (!date.isAfter(until.toLocalDate())) {
            result += ReminderPlanner.forDay(date, plan(context, owner, date), settings)
            val partnerPlan = if (settings.partner.study) plan(context, owner.partner, date) else emptyList()
            result += ReminderPlanner.forPartnerDay(date, partnerPlan, settings)
            date = date.plusDays(1)
        }
        return result.filter { it.at.isAfter(from) && !it.at.isAfter(until) }
    }

    fun scheduleNext(context: Context) {
        val settings = AppSettings(context)
        val now = LocalDateTime.now()
        val last = settings.lastFiredMillis.takeIf { it > 0 }?.let { toDateTime(it) }
        val from = if (last != null && last.isAfter(now)) last else now
        val next = runCatching { reminders(context, from, from.plusDays(2)) }
            .getOrDefault(emptyList())
            .minByOrNull { toMillis(it.at) }

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = alarmIntent(context)
        if (next == null) {
            alarmManager.cancel(intent)
            settings.nextAlarmMillis = 0L
            return
        }
        val trigger = toMillis(next.at)
        try {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
        }
        settings.nextAlarmMillis = trigger
    }

    /** Сработал будильник: показать то, что пора, проверить сайт, поставить следующий. */
    suspend fun handleAlarm(context: Context) {
        val settings = AppSettings(context)
        val now = LocalDateTime.now()
        val last = settings.lastFiredMillis.takeIf { it > 0 }?.let { toDateTime(it) } ?: now.minusMinutes(30)
        val from = if (last.isBefore(now.minusHours(12))) now.minusHours(12) else last
        val until = now.plusSeconds(30)
        val due = runCatching { reminders(context, from, until) }.getOrDefault(emptyList())
            .filter { Duration.between(it.at, now) <= ReminderPlanner.maxDelay(it.kind) }
        settings.lastFiredMillis = toMillis(until)

        if (due.any { it.kind == ReminderKind.CHECK }) {
            // Сначала пробуем сразу. Если сети нет, проверка повторится, когда она появится.
            val ok = runCatching { ScheduleSync.checkAll(context, timeoutMs = 15_000) }.getOrDefault(false)
            if (!ok) ScheduleCheckWorker.runNow(context)
            // Заодно обновить погоду, чтобы утренняя сводка не ждала сеть.
            runCatching { WeatherRepository.fetch(context, timeoutMs = 10_000) }
        }
        due.filter { it.kind != ReminderKind.CHECK }.forEach { reminder ->
            runCatching { show(context, reminder) }
        }
        scheduleNext(context)
    }

    private suspend fun show(context: Context, reminder: Reminder) {
        val date = reminder.at.toLocalDate()
        val owner = AppSettings(context).owner ?: return
        val person = if (reminder.partner) owner.partner else owner
        when (reminder.kind) {
            ReminderKind.CHECK -> Unit
            ReminderKind.MORNING -> {
                val items = dayItems(context, person, date)
                // План учёбы — только свой: отметки второго человека хранятся на его телефоне.
                val plan = if (reminder.partner) emptyList() else plan(context, person, date)
                val forecast = runCatching { WeatherRepository.forecast(context) }.getOrNull()
                val text = Summaries.morning(date, items, plan, forecast, person.profile)
                val title = if (reminder.partner) "Сегодня у ${person.genitive}" else person.profile.morningTitle
                Notifier.morning(context, title, text, reminder.partner)
            }
            ReminderKind.BLOCK -> {
                val block = plan(context, person, date).firstOrNull { it.kind == reminder.block } ?: return
                // План мог сдвинуться после обновления расписания — тогда напомним в новое время.
                if (block.start != reminder.at.toLocalTime()) return
                if (reminder.partner) {
                    Notifier.block(context, date, block, partner = person)
                    return
                }
                if (block.key(date) in ProgressRepository.current(context.filesDir)) return
                Notifier.block(context, date, block)
            }
            ReminderKind.WEATHER -> {
                val tomorrow = date.plusDays(1)
                val forecast = runCatching { WeatherRepository.forecast(context) }.getOrNull()
                val note = WeatherAdvice.tomorrowNote(
                    tomorrow,
                    dayItems(context, person, tomorrow),
                    forecast,
                    person.profile.leaveBeforeMinutes,
                ) ?: return
                val title = if (reminder.partner) "${person.shortName} · ${note.title}" else note.title
                Notifier.weather(context, title, note.text, reminder.partner)
            }
            ReminderKind.EVENING -> {
                if (reminder.partner) return
                val done = ProgressRepository.current(context.filesDir)
                val text = Summaries.evening(date, plan(context, person, date), done) ?: return
                Notifier.evening(context, text)
            }
        }
    }

    fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun toMillis(time: LocalDateTime): Long =
        time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun toDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
}
