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
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.data.ReminderSettings
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
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

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScheduleSync.repository(app)
    private val appSettings = AppSettings(app)

    /** Отметки «сделал»: ключ «дата|вид блока» -> минуты. */
    val done = mutableStateMapOf<String, Int>()

    /** Загруженные недели по дате понедельника. */
    val weeks = mutableStateMapOf<LocalDate, WeekSchedule>()

    /** Недели, которые сейчас скачиваются. */
    val loading = mutableStateListOf<LocalDate>()

    var error by mutableStateOf<String?>(null)
        private set

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
        for (offset in 0..1) {
            repository.loadCached(monday.plusWeeks(offset.toLong()))?.let { weeks[it.weekStart] = it }
        }
        refresh(0)
        refresh(1)

        ScheduleCheckWorker.schedulePeriodic(app)
        rescheduleReminders()
    }

    fun toggleDone(date: LocalDate, block: PlanBlock) {
        ProgressRepository.toggle(getApplication<Application>().filesDir, block.key(date), block.minutes)
    }

    fun isLoading(offset: Int): Boolean =
        weekStartFor(LocalDate.now()).plusWeeks(offset.toLong()) in loading

    /** Обновить неделю с сайта: 0 — текущая, 1 — следующая. */
    fun refresh(offset: Int) {
        val start = weekStartFor(LocalDate.now()).plusWeeks(offset.toLong())
        if (start in loading) return
        loading += start
        viewModelScope.launch {
            try {
                val result = ScheduleSync.syncWeek(getApplication<Application>(), offset)
                weeks[result.week.weekStart] = result.week
                error = null
                rescheduleReminders()
            } catch (e: Exception) {
                error = describe(e)
            } finally {
                loading -= start
            }
        }
    }

    /** Показать неделю: сначала из памяти телефона, потом свежую с сайта. */
    fun ensureWeek(offset: Int) {
        val start = weekStartFor(LocalDate.now()).plusWeeks(offset.toLong())
        if (start !in weeks) {
            repository.loadCached(start)?.let { weeks[start] = it }
        }
        val fetched = weeks[start]?.fetchedAtMillis ?: 0L
        val stale = System.currentTimeMillis() - fetched > STALE_AFTER_MS
        if (stale) refresh(offset)
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

    /** Проверить расписание прямо сейчас (кнопка в настройках). */
    fun checkNow() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                ScheduleSync.checkAll(getApplication<Application>())
                val monday = weekStartFor(LocalDate.now())
                for (offset in 0..1) {
                    repository.loadCached(monday.plusWeeks(offset.toLong()))?.let { weeks[it.weekStart] = it }
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
            ?.let { "Последняя проверка сайта: ${formatStamp(it)}, ${appSettings.lastCheckResult}." }
            ?: "Сайт ещё не проверялся в фоне."
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

    private fun describe(e: Exception): String = when (e) {
        is HttpStatusException -> "Сайт расписания ответил ошибкой ${e.statusCode}. Показано сохранённое расписание."
        is IOException -> "Нет связи с сайтом расписания. Показано сохранённое расписание."
        else -> "Не получилось обновить расписание: ${e.javaClass.simpleName}."
    }

    private companion object {
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
