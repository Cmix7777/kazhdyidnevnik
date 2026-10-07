package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** Отметки «сделал»: ключ «дата|вид блока» -> минуты. Хранится в файле на телефоне. */
class ProgressStore(private val file: File) {

    @Serializable
    private data class ProgressDto(val done: Map<String, Int> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Map<String, Int> =
        if (!file.exists()) emptyMap()
        else runCatching { json.decodeFromString(ProgressDto.serializer(), file.readText()).done }
            .getOrDefault(emptyMap())

    fun save(done: Map<String, Int>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ProgressDto.serializer(), ProgressDto(done)))
    }
}

/**
 * Общие на всё приложение отметки «сделал». Ими пользуются и экраны,
 * и кнопка «Сделал» в уведомлении, поэтому они всегда совпадают.
 */
object ProgressRepository {

    private val lock = Any()
    private var store: ProgressStore? = null
    private val mutableState = MutableStateFlow<Map<String, Int>>(emptyMap())

    val state: StateFlow<Map<String, Int>> = mutableState.asStateFlow()

    /** Загрузить отметки из файла (один раз за запуск). */
    fun init(filesDir: File) {
        synchronized(lock) {
            if (store == null) {
                val loaded = ProgressStore(File(filesDir, "progress.json"))
                store = loaded
                mutableState.value = loaded.load()
            }
        }
    }

    fun current(filesDir: File): Map<String, Int> {
        init(filesDir)
        return mutableState.value
    }

    /** Поставить или снять отметку. */
    fun toggle(filesDir: File, key: String, minutes: Int) {
        update(filesDir) { done ->
            if (done.remove(key) == null) done[key] = minutes
        }
    }

    fun markDone(filesDir: File, key: String, minutes: Int) {
        update(filesDir) { done -> done[key] = minutes }
    }

    /** Добавить отметки из резервной копии. Возвращает, сколько отметок было новыми. */
    fun mergeAll(filesDir: File, incoming: Map<String, Int>): Int {
        var added = 0
        update(filesDir) { done ->
            added = incoming.keys.count { it !in done }
            val merged = Backup.merge(done, incoming)
            done.clear()
            done.putAll(merged)
        }
        return added
    }

    private fun update(filesDir: File, change: (MutableMap<String, Int>) -> Unit) {
        init(filesDir)
        synchronized(lock) {
            val done = mutableState.value.toMutableMap()
            change(done)
            store?.save(done)
            mutableState.value = done
        }
    }
}

/** Разобранный ключ отметки. */
data class DoneEntry(val date: LocalDate, val kind: PlanKind, val minutes: Int)

object ProgressStats {

    fun entries(done: Map<String, Int>): List<DoneEntry> = done.mapNotNull { (key, minutes) ->
        val parts = key.split("|")
        if (parts.size != 2) return@mapNotNull null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
        val kind = PlanKind.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
        DoneEntry(date, kind, minutes)
    }

    /** Сколько дней подряд решались билеты ПДД (сегодня можно ещё не успеть). */
    fun pddStreak(done: Map<String, Int>, today: LocalDate): Int {
        var day = if (progressKey(today, PlanKind.PDD) in done) today else today.minusDays(1)
        var count = 0
        while (progressKey(day, PlanKind.PDD) in done) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Отметки ПДД за последние [days] дней, от старых к новым. */
    fun pddHistory(done: Map<String, Int>, today: LocalDate, days: Int = 14): List<Boolean> =
        (days - 1 downTo 0).map { progressKey(today.minusDays(it.toLong()), PlanKind.PDD) in done }

    fun minutesInWeek(done: Map<String, Int>, monday: LocalDate): Int {
        val first = monday.toEpochDay()
        val last = first + 6
        return entries(done).filter { it.date.toEpochDay() in first..last }.sumOf { it.minutes }
    }

    fun count(done: Map<String, Int>, kind: PlanKind): Int = entries(done).count { it.kind == kind }

    /** Сколько занятий сделано по теме недели [topicNumber] (только первый проход программы). */
    fun topicSessions(done: Map<String, Int>, kind: PlanKind, topicNumber: Int): Int =
        entries(done).count { it.kind == kind && Curriculum.weekNumber(it.date) == topicNumber }
}

/** «2 ч 15 мин», «45 мин», «0 мин». */
fun formatMinutes(total: Int): String {
    val hours = total / 60
    val minutes = total % 60
    return when {
        hours == 0 -> "$minutes мин"
        minutes == 0 -> "$hours ч"
        else -> "$hours ч $minutes мин"
    }
}
