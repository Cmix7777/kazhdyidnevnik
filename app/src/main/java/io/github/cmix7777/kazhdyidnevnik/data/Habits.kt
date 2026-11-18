package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * Привычка: человек сам её заводит и каждый день отмечает. [done] — дни, когда отмечена (yyyy-MM-dd).
 * [remindHour] — час утреннего напоминания или null. [shared] — видно ли привычку второму человеку.
 */
@Serializable
data class Habit(
    @SerialName("i") val id: String,
    @SerialName("t") val title: String,
    @SerialName("h") val remindHour: Int? = null,
    @SerialName("s") val shared: Boolean = true,
    @SerialName("d") val done: List<String> = emptyList(),
    @SerialName("at") val created: Long = 0L,
)

object Habits {

    const val MAX_TITLE = 60
    const val MAX_HABITS = 30
    val remindHours = listOf(6, 7, 8, 9)
    private const val KEEP_DAYS = 400L
    private const val SHARE_DAYS = 60L

    fun add(habits: List<Habit>, id: String, title: String, now: Long): List<Habit> {
        val clean = title.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE)
        if (clean.isEmpty() || habits.size >= MAX_HABITS || habits.any { it.id == id }) return habits
        return habits + Habit(id = id, title = clean, created = now)
    }

    fun isDone(habit: Habit, date: LocalDate): Boolean = date.toString() in habit.done

    fun toggle(habits: List<Habit>, id: String, date: LocalDate): List<Habit> = habits.map { habit ->
        if (habit.id != id) return@map habit
        val key = date.toString()
        val next = if (key in habit.done) habit.done - key else habit.done + key
        val oldest = date.minusDays(KEEP_DAYS)
        habit.copy(done = next.filter { runCatching { !LocalDate.parse(it).isBefore(oldest) }.getOrDefault(false) }.sorted())
    }

    fun setReminder(habits: List<Habit>, id: String, hour: Int?): List<Habit> =
        habits.map { if (it.id == id) it.copy(remindHour = hour) else it }

    fun setShared(habits: List<Habit>, id: String, shared: Boolean): List<Habit> =
        habits.map { if (it.id == id) it.copy(shared = shared) else it }

    fun remove(habits: List<Habit>, id: String): List<Habit> = habits.filterNot { it.id == id }

    /** Восстановление из копии: недостающие привычки добавляются, у совпадающих отметки объединяются. */
    fun mergeBackup(current: List<Habit>, incoming: List<Habit>): List<Habit> {
        val byId = current.associateBy { it.id }
        val updated = current.map { habit ->
            val other = incoming.firstOrNull { it.id == habit.id } ?: return@map habit
            habit.copy(done = (habit.done + other.done).distinct().sorted())
        }
        return (updated + incoming.filter { it.id !in byId }).take(MAX_HABITS)
    }

    /** Сколько дней подряд привычка держится: считая сегодня, а если сегодня ещё не отмечена — до вчера. */
    fun streak(habit: Habit, today: LocalDate): Int {
        val days = habit.done.toSet()
        var day = if (today.toString() in days) today else today.minusDays(1)
        var count = 0
        while (day.toString() in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Отметки за последние [days] дней, сегодня последним. */
    fun history(habit: Habit, today: LocalDate, days: Int = 14): List<Boolean> {
        val set = habit.done.toSet()
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()).toString() in set }
    }

    /** Что уходит второму телефону: только открытые привычки, без напоминаний и с отметками за последние два месяца. */
    fun shareable(habits: List<Habit>, today: LocalDate): List<Habit> {
        val oldest = today.minusDays(SHARE_DAYS).toString()
        return habits.filter { it.shared }.map { habit -> habit.copy(remindHour = null, done = habit.done.filter { it >= oldest }) }
    }
}

/** Привычки на этом телефоне (файл habits.json). */
object HabitStore {

    private val lock = Any()
    private var file: File? = null
    private val mutableState = MutableStateFlow<List<Habit>>(emptyList())
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Habit.serializer())

    val state: StateFlow<List<Habit>> = mutableState.asStateFlow()

    fun init(filesDir: File) {
        synchronized(lock) {
            if (file == null) {
                val target = File(filesDir, FILE)
                file = target
                mutableState.value = if (target.exists()) {
                    runCatching { json.decodeFromString(serializer, target.readText()) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
        }
    }

    fun current(filesDir: File): List<Habit> {
        init(filesDir)
        return mutableState.value
    }

    fun update(filesDir: File, change: (List<Habit>) -> List<Habit>): List<Habit> = synchronized(lock) {
        init(filesDir)
        val next = change(mutableState.value)
        if (next != mutableState.value) {
            runCatching { File(filesDir, FILE).writeText(json.encodeToString(serializer, next)) }
            mutableState.value = next
        }
        next
    }

    private const val FILE = "habits.json"
}
