package io.github.cmix7777.kazhdyidnevnik.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.PlanGenerator
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.data.Reminder
import io.github.cmix7777.kazhdyidnevnik.data.ReminderKind
import io.github.cmix7777.kazhdyidnevnik.data.ReminderPlanner
import io.github.cmix7777.kazhdyidnevnik.data.Summaries
import io.github.cmix7777.kazhdyidnevnik.data.WorkSchedule
import io.github.cmix7777.kazhdyidnevnik.data.buildDay
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
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

    /** Пары и смены дня из сохранённого расписания или null, если неделя не загружена. */
    fun dayItems(context: Context, date: LocalDate): List<DayItem>? =
        ScheduleSync.repository(context).loadCached(weekStartFor(date))
            ?.let { buildDay(date, it.lessons, WorkSchedule.default) }

    fun plan(context: Context, date: LocalDate): List<PlanBlock> =
        dayItems(context, date)?.let { PlanGenerator.planFor(date, it) }.orEmpty()

    /** Напоминания в промежутке (from, until]. */
    private fun reminders(context: Context, from: LocalDateTime, until: LocalDateTime): List<Reminder> {
        val settings = AppSettings(context).reminders
        val result = mutableListOf<Reminder>()
        var date = from.toLocalDate()
        while (!date.isAfter(until.toLocalDate())) {
            result += ReminderPlanner.forDay(date, plan(context, date), settings)
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
        }
        due.filter { it.kind != ReminderKind.CHECK }.forEach { reminder ->
            runCatching { show(context, reminder) }
        }
        scheduleNext(context)
    }

    private fun show(context: Context, reminder: Reminder) {
        val date = reminder.at.toLocalDate()
        when (reminder.kind) {
            ReminderKind.CHECK -> Unit
            ReminderKind.MORNING -> {
                val items = dayItems(context, date)
                val plan = items?.let { PlanGenerator.planFor(date, it) }.orEmpty()
                Notifier.morning(context, Summaries.morning(date, items, plan))
            }
            ReminderKind.BLOCK -> {
                val block = plan(context, date).firstOrNull { it.kind == reminder.block } ?: return
                // План мог сдвинуться после обновления расписания — тогда напомним в новое время.
                if (block.start != reminder.at.toLocalTime()) return
                if (block.key(date) in ProgressRepository.current(context.filesDir)) return
                Notifier.block(context, date, block)
            }
            ReminderKind.EVENING -> {
                val done = ProgressRepository.current(context.filesDir)
                val text = Summaries.evening(date, plan(context, date), done) ?: return
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
