package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Пункт общего списка (дела, покупки). Список живёт на обоих телефонах и ходит вместе с делами:
 * каждый телефон отдаёт свою копию целиком, а при встрече двух копий по каждому пункту побеждает
 * самое позднее изменение ([updated]). Удалённый пункт остаётся «надгробием», чтобы не воскреснуть
 * из копии второго телефона, и через месяц забывается.
 */
@Serializable
data class ListItem(
    @SerialName("i") val id: String,
    @SerialName("t") val text: String,
    @SerialName("by") val by: String,
    @SerialName("at") val at: Long,
    @SerialName("u") val updated: Long,
    @SerialName("d") val done: Boolean = false,
    @SerialName("x") val deleted: Boolean = false,
)

object SharedList {

    const val MAX_TEXT = 200
    const val MAX_ITEMS = 300
    private const val FORGET_MS = 30L * 24 * 60 * 60 * 1000

    fun add(items: List<ListItem>, id: String, text: String, by: Person, now: Long): List<ListItem> {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_TEXT)
        if (clean.isEmpty() || items.any { it.id == id }) return items
        return items + ListItem(id, clean, by.id, now, now)
    }

    fun toggle(items: List<ListItem>, id: String, now: Long): List<ListItem> =
        items.map { if (it.id == id && !it.deleted) it.copy(done = !it.done, updated = now) else it }

    fun remove(items: List<ListItem>, id: String, now: Long): List<ListItem> =
        items.map { if (it.id == id) it.copy(deleted = true, updated = now) else it }

    fun clearDone(items: List<ListItem>, now: Long): List<ListItem> =
        items.map { if (it.done && !it.deleted) it.copy(deleted = true, updated = now) else it }

    /** Соединить две копии: по каждому пункту — более позднее изменение; при равном времени удаление и «сделано» сильнее. */
    fun merge(mine: List<ListItem>, theirs: List<ListItem>): List<ListItem> {
        val byId = LinkedHashMap<String, ListItem>()
        for (item in mine + theirs) {
            val old = byId[item.id]
            byId[item.id] = when {
                old == null -> item
                item.updated > old.updated -> item
                item.updated < old.updated -> old
                else -> old.copy(done = old.done || item.done, deleted = old.deleted || item.deleted)
            }
        }
        return byId.values.sortedBy { it.at }
    }

    /** Старые надгробия забываются, а список не растёт бесконечно. */
    fun prune(items: List<ListItem>, now: Long): List<ListItem> =
        items.filter { !(it.deleted && now - it.updated > FORGET_MS) }.takeLast(MAX_ITEMS)

    /** Что показать: сначала несделанное по порядку добавления, потом сделанное. */
    fun visible(items: List<ListItem>): List<ListItem> =
        items.filter { !it.deleted }.sortedWith(compareBy<ListItem>({ it.done }, { it.at }))
}

/** Общий список на этом телефоне (файл shared-list.json). */
object SharedListStore {

    private val lock = Any()
    private var file: File? = null
    private val mutableState = MutableStateFlow<List<ListItem>>(emptyList())
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ListItem.serializer())

    val state: StateFlow<List<ListItem>> = mutableState.asStateFlow()

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

    fun current(filesDir: File): List<ListItem> {
        init(filesDir)
        return mutableState.value
    }

    /** Изменить список; возвращает новый. */
    fun update(filesDir: File, change: (List<ListItem>) -> List<ListItem>): List<ListItem> = synchronized(lock) {
        init(filesDir)
        val next = change(mutableState.value)
        if (next != mutableState.value) {
            runCatching { File(filesDir, FILE).writeText(json.encodeToString(serializer, next)) }
            mutableState.value = next
        }
        next
    }

    private const val FILE = "shared-list.json"
}
