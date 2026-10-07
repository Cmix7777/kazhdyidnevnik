package io.github.cmix7777.kazhdyidnevnik.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.ProgressStore
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleRepository
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import kotlinx.coroutines.launch
import org.jsoup.HttpStatusException
import java.io.File
import java.io.IOException
import java.time.LocalDate

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ScheduleRepository(File(app.filesDir, "schedule"))
    private val progressStore = ProgressStore(File(app.filesDir, "progress.json"))

    /** Отметки «сделал»: ключ «дата|вид блока» -> минуты. */
    val done = mutableStateMapOf<String, Int>().apply { putAll(progressStore.load()) }

    fun toggleDone(date: LocalDate, block: PlanBlock) {
        val key = block.key(date)
        if (key in done) done.remove(key) else done[key] = block.minutes
        progressStore.save(done.toMap())
    }

    /** Загруженные недели по дате понедельника. */
    val weeks = mutableStateMapOf<LocalDate, WeekSchedule>()

    /** Недели, которые сейчас скачиваются. */
    val loading = mutableStateListOf<LocalDate>()

    var error by mutableStateOf<String?>(null)
        private set

    init {
        val monday = weekStartFor(LocalDate.now())
        for (offset in 0..1) {
            repository.loadCached(monday.plusWeeks(offset.toLong()))?.let { weeks[it.weekStart] = it }
        }
        refresh(0)
        refresh(1)
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
                val week = repository.fetch(offset)
                weeks[week.weekStart] = week
                error = null
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

    private fun describe(e: Exception): String = when (e) {
        is HttpStatusException -> "Сайт расписания ответил ошибкой ${e.statusCode}. Показано сохранённое расписание."
        is IOException -> "Нет связи с сайтом расписания. Показано сохранённое расписание."
        else -> "Не получилось обновить расписание: ${e.javaClass.simpleName}."
    }

    private companion object {
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
