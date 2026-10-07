package io.github.cmix7777.kazhdyidnevnik.notify

import android.content.Context
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleChange
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleDiff
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleRepository
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.HttpStatusException
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * Скачивает расписание, сравнивает с сохранённым и сообщает об изменениях.
 * Используется и приложением, и фоновой проверкой.
 */
object ScheduleSync {

    data class Result(val week: WeekSchedule, val changes: List<ScheduleChange>)

    private val mutex = Mutex()

    fun repository(context: Context) = ScheduleRepository(File(context.filesDir, "schedule"))

    suspend fun syncWeek(
        context: Context,
        offset: Int,
        today: LocalDate = LocalDate.now(),
        timeoutMs: Int = 20_000,
    ): Result {
        val repository = repository(context)
        val fresh = repository.download(offset, today, timeoutMs)
        return mutex.withLock {
            val settings = AppSettings(context)
            val old = repository.loadCached(fresh.weekStart)

            // Сайт вдруг отдал пустую неделю, хотя пары впереди были. Такое бывает при сбоях,
            // поэтому верим только второй проверке подряд.
            if (old != null && fresh.lessons.isEmpty() && old.lessons.any { !it.date.isBefore(today) } &&
                !settings.isEmptyPending(fresh.weekStart)
            ) {
                settings.setEmptyPending(fresh.weekStart, true)
                return@withLock Result(old, emptyList())
            }
            settings.setEmptyPending(fresh.weekStart, false)

            repository.save(fresh)
            val changes = if (old == null) emptyList() else ScheduleDiff.diff(old.lessons, fresh.lessons, today)
            if (changes.isNotEmpty() && settings.reminders.changes) {
                Notifier.scheduleChanged(context, fresh.weekStart, changes)
            }
            Result(fresh, changes)
        }
    }

    /** Проверить текущую и следующую неделю. true — сайт ответил. */
    suspend fun checkAll(context: Context, timeoutMs: Int = 20_000): Boolean {
        val today = LocalDate.now()
        val results = coroutineScope {
            (0..1).map { offset ->
                async { runCatching { syncWeek(context, offset, today, timeoutMs) } }
            }.awaitAll()
        }
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        val changes = results.sumOf { it.getOrNull()?.changes?.size ?: 0 }
        val summary = when {
            failure != null -> "не получилось: ${describeError(failure)}"
            changes == 0 -> "изменений нет"
            else -> "изменений: $changes"
        }
        AppSettings(context).recordCheck(System.currentTimeMillis(), summary)
        ReminderScheduler.scheduleNext(context)
        return failure == null
    }

    fun describeError(e: Throwable): String = when (e) {
        is HttpStatusException -> "сайт ответил ошибкой ${e.statusCode}"
        is IOException -> "нет связи с сайтом"
        else -> e.javaClass.simpleName
    }
}
