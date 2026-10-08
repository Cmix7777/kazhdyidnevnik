package io.github.cmix7777.kazhdyidnevnik.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.data.ReminderSettings
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetFormatException
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.formatStamp
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.Notifier
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import io.github.cmix7777.kazhdyidnevnik.notify.ScheduleCheckWorker
import io.github.cmix7777.kazhdyidnevnik.notify.ScheduleSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import java.io.IOException
import java.time.LocalDate

/** Расписание одного человека на экране: загруженные недели, что качается, ошибка. */
class PersonSchedule(val person: Person) {
    /** Загруженные недели по дате понедельника. */
    val weeks = mutableStateMapOf<LocalDate, WeekSchedule>()

    /** Недели, которые сейчас скачиваются. */
    val loading = mutableStateListOf<LocalDate>()

    var error by mutableStateOf<String?>(null)
}

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val appSettings = AppSettings(app)

    /** Чей это телефон. null — ещё не выбрано. */
    var owner by mutableStateOf(appSettings.owner)
        private set

    private val people: Map<Person, PersonSchedule> = Person.entries.associateWith { PersonSchedule(it) }

    /** Отметки «сделал» владельца: ключ «дата|вид блока» -> минуты. */
    val done = mutableStateMapOf<String, Int>()

    var reminderSettings by mutableStateOf(appSettings.reminders)
        private set

    /** Идёт ручная проверка расписания из настроек. */
    var checking by mutableStateOf(false)
        private set

    /** Меняется после проверки, чтобы экран настроек перечитал статус. */
    var statusVersion by mutableIntStateOf(0)
        private set

    init {
        ProgressRepository.init(app.filesDir)
        viewModelScope.launch {
            ProgressRepository.state.collect { latest ->
                if (latest != done.toMap()) {
                    done.clear()
                    done.putAll(latest)
                }
            }
        }

        val monday = weekStartFor(LocalDate.now())
        for (person in Person.entries) {
            val repository = ScheduleSync.repository(app, person)
            for (offset in 0..1) {
                repository.loadOrBuild(monday.plusWeeks(offset.toLong()))?.let { schedule(person).weeks[it.weekStart] = it }
            }
            refresh(person, 0)
            refresh(person, 1)
        }

        ScheduleCheckWorker.schedulePeriodic(app)
        rescheduleReminders()
    }

    fun schedule(person: Person): PersonSchedule = people.getValue(person)

    /** Выбрать, чей это телефон: от этого зависят свои уведомления и первая страница. */
    fun chooseOwner(person: Person) {
        appSettings.owner = person
        owner = person
        rescheduleReminders()
    }

    fun toggleDone(date: LocalDate, block: PlanBlock) {
        ProgressRepository.toggle(getApplication<Application>().filesDir, block.key(date), block.minutes)
    }

    fun isLoading(person: Person, offset: Int): Boolean =
        weekStartFor(LocalDate.now()).plusWeeks(offset.toLong()) in schedule(person).loading

    /** Обновить неделю с сайта: 0 — текущая, 1 — следующая. */
    fun refresh(person: Person, offset: Int) {
        val state = schedule(person)
        val start = weekStartFor(LocalDate.now()).plusWeeks(offset.toLong())
        if (start in state.loading) return
        state.loading += start
        viewModelScope.launch {
            try {
                val result = ScheduleSync.syncWeek(getApplication<Application>(), person, offset)
                state.weeks[result.week.weekStart] = result.week
                state.error = null
                rescheduleReminders()
            } catch (e: Exception) {
                state.error = describe(person, e)
            } finally {
                state.loading -= start
            }
        }
    }

    /** Показать неделю: сначала из памяти телефона, потом свежую с сайта. */
    fun ensureWeek(person: Person, offset: Int) {
        val state = schedule(person)
        val start = weekStartFor(LocalDate.now()).plusWeeks(offset.toLong())
        if (start !in state.weeks) {
            ScheduleSync.repository(getApplication<Application>(), person).loadOrBuild(start)?.let { state.weeks[start] = it }
        }
        val fetched = state.weeks[start]?.fetchedAtMillis ?: 0L
        val stale = System.currentTimeMillis() - fetched > STALE_AFTER_MS
        if (stale) refresh(person, offset)
    }

    fun updateReminders(change: (ReminderSettings) -> ReminderSettings) {
        val updated = change(reminderSettings)
        reminderSettings = updated
        appSettings.reminders = updated
        rescheduleReminders()
    }

    /** Перечитать настройки уведомлений (после восстановления из копии). */
    fun reloadSettings() {
        reminderSettings = appSettings.reminders
        rescheduleReminders()
    }

    /** Проверить расписания прямо сейчас (кнопка в настройках). */
    fun checkNow() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                ScheduleSync.checkAll(getApplication<Application>())
                val monday = weekStartFor(LocalDate.now())
                for (person in Person.entries) {
                    val repository = ScheduleSync.repository(getApplication<Application>(), person)
                    for (offset in 0..1) {
                        repository.loadCached(monday.plusWeeks(offset.toLong()))?.let { schedule(person).weeks[it.weekStart] = it }
                    }
                }
            } finally {
                checking = false
                statusVersion++
            }
        }
    }

    fun testNotification() = Notifier.test(getApplication<Application>())

    /** Спрашивали ли уже разрешение на уведомления при первом запуске. */
    fun shouldAskNotifications(): Boolean = !appSettings.notificationsAsked

    fun markNotificationsAsked() {
        appSettings.notificationsAsked = true
    }

    /** Строки состояния для экрана настроек. */
    fun statusText(): String {
        val check = appSettings.lastCheckMillis.takeIf { it > 0 }
            ?.let { "Последняя проверка расписаний: ${formatStamp(it)}. ${appSettings.lastCheckResult}." }
            ?: "Расписания ещё не проверялись в фоне."
        val next = appSettings.nextAlarmMillis.takeIf { it > System.currentTimeMillis() }
            ?.let { "Следующее напоминание или проверка: ${formatStamp(it)}." }
        return listOfNotNull(check, next).joinToString("\n")
    }

    private fun rescheduleReminders() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ReminderScheduler.scheduleNext(app) }
            statusVersion++
        }
    }

    private fun describe(person: Person, e: Exception): String {
        val site = if (person == Person.AIZAT) "Сайт расписания" else "Сайт колледжа"
        return when (e) {
            is HttpStatusException -> "$site ответил ошибкой ${e.statusCode}. Показано сохранённое расписание."
            is IOException -> "Нет связи с сайтом расписания. Показано сохранённое расписание."
            is SheetFormatException -> "Не получилось прочитать таблицу колледжа: ${e.message}. Показано сохранённое расписание."
            else -> "Не получилось обновить расписание: ${e.javaClass.simpleName}."
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
