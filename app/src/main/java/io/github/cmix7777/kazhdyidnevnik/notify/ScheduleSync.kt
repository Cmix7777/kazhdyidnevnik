package io.github.cmix7777.kazhdyidnevnik.notify

import android.content.Context
import io.github.cmix7777.kazhdyidnevnik.data.CollegeRepository
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleChange
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleDiff
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleRepository
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleSource
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetFormatException
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
 * Используется и приложением, и фоновой проверкой. Расписаний два: Айзата и Насти.
 */
object ScheduleSync {

    data class Result(val week: WeekSchedule, val changes: List<ScheduleChange>)

    private val mutex = Mutex()

    fun repository(context: Context, person: Person): ScheduleSource = when (person) {
        Person.AIZAT -> ScheduleRepository(File(context.filesDir, "schedule"))
        Person.NASTYA -> CollegeRepository(File(context.filesDir, "schedule-nastya"))
    }

    suspend fun syncWeek(
        context: Context,
        person: Person,
        offset: Int,
        today: LocalDate = LocalDate.now(),
        timeoutMs: Int = 20_000,
    ): Result {
        val repository = repository(context, person)
        val fresh = repository.download(offset, today, timeoutMs)
        return mutex.withLock {
            val settings = AppSettings(context)
            val old = repository.loadCached(fresh.weekStart)

            // Сайт вдруг отдал пустую неделю, хотя пары впереди были. Такое бывает при сбоях,
            // поэтому верим только второй проверке подряд.
            if (old != null && fresh.lessons.isEmpty() && old.lessons.any { !it.date.isBefore(today) } &&
                !settings.isEmptyPending(person, fresh.weekStart)
            ) {
                settings.setEmptyPending(person, fresh.weekStart, true)
                return@withLock Result(old, emptyList())
            }
            settings.setEmptyPending(person, fresh.weekStart, false)

            repository.save(fresh)
            val changes = if (old == null) emptyList() else ScheduleDiff.diff(old.lessons, fresh.lessons, today)
            if (changes.isNotEmpty() && wantsChanges(settings, person)) {
                Notifier.scheduleChanged(context, person, settings.owner, fresh.weekStart, changes)
            }
            Result(fresh, changes)
        }
    }

    /** Присылать ли уведомление об изменениях в расписании этого человека. */
    private fun wantsChanges(settings: AppSettings, person: Person): Boolean {
        val owner = settings.owner ?: return false
        val reminders = settings.reminders
        return if (person == owner) reminders.changes else reminders.partner.changes
    }

    /** Проверить текущую и следующую неделю у обоих. true — оба сайта ответили. */
    suspend fun checkAll(context: Context, timeoutMs: Int = 20_000): Boolean {
        val today = LocalDate.now()
        val results = coroutineScope {
            Person.entries.associateWith { person ->
                (0..1).map { offset ->
                    async { runCatching { syncWeek(context, person, offset, today, timeoutMs) } }
                }
            }.mapValues { (_, jobs) -> jobs.awaitAll() }
        }
        val parts = results.map { (person, list) ->
            val failure = list.firstNotNullOfOrNull { it.exceptionOrNull() }
            val changes = list.sumOf { it.getOrNull()?.changes?.size ?: 0 }
            val text = when {
                failure != null -> "не получилось: ${describeError(failure)}"
                changes == 0 -> "изменений нет"
                else -> "изменений: $changes"
            }
            "${person.shortName} — $text"
        }
        AppSettings(context).recordCheck(System.currentTimeMillis(), parts.joinToString("; "))
        ReminderScheduler.scheduleNext(context)
        return results.values.flatten().all { it.isSuccess }
    }

    fun describeError(e: Throwable): String = when (e) {
        is HttpStatusException -> "сайт ответил ошибкой ${e.statusCode}"
        is IOException -> "нет связи с сайтом"
        is SheetFormatException -> e.message ?: "файл расписания не читается"
        else -> e.javaClass.simpleName
    }
}
