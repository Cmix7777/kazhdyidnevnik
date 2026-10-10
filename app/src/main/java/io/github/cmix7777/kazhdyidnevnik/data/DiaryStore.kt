package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@Serializable
internal data class EventDto(
    val id: String,
    val kind: String,
    val title: String = "",
    val date: String,
    val start: String,
    val end: String,
    val weekly: Boolean = false,
    val skipped: List<String> = emptyList(),
    val remindBefore: Int? = null,
    val note: String = "",
)

@Serializable
internal data class DeadlineDto(
    val id: String,
    val title: String,
    val date: String,
    val note: String? = null,
)

@Serializable
internal data class MoneyDto(
    val id: String,
    val date: String,
    val amount: Long,
    val income: Boolean,
    val category: String,
    val note: String = "",
    val createdAt: Long = 0L,
)

@Serializable
internal data class DiaryDto(
    val events: List<EventDto> = emptyList(),
    val deadlines: List<DeadlineDto> = emptyList(),
    val money: List<MoneyDto> = emptyList(),
    val seeded: List<String> = emptyList(),
)

/** Дела, даты и бюджет в JSON. Битые записи пропускаются, остальные читаются. */
object DiaryCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    internal fun toDto(diary: Diary): DiaryDto = DiaryDto(
        events = diary.events.map {
            EventDto(
                id = it.id,
                kind = it.kind.name,
                title = it.title,
                date = it.date.toString(),
                start = it.start.toString(),
                end = it.end.toString(),
                weekly = it.weekly,
                skipped = it.skipped.map(LocalDate::toString).sorted(),
                remindBefore = it.remindBefore,
                note = it.note,
            )
        },
        deadlines = diary.deadlines.mapNotNull { d ->
            d.id?.let { DeadlineDto(id = it, title = d.title, date = d.date.toString(), note = d.note) }
        },
        money = diary.money.map {
            MoneyDto(
                id = it.id,
                date = it.date.toString(),
                amount = it.amount,
                income = it.income,
                category = it.category,
                note = it.note,
                createdAt = it.createdAt,
            )
        },
        seeded = diary.seeded.sorted(),
    )

    internal fun fromDto(dto: DiaryDto): Diary = Diary(
        events = dto.events.mapNotNull { e ->
            runCatching {
                UserEvent(
                    id = e.id,
                    kind = runCatching { EventKind.valueOf(e.kind) }.getOrDefault(EventKind.OTHER),
                    title = e.title,
                    date = LocalDate.parse(e.date),
                    start = LocalTime.parse(e.start),
                    end = LocalTime.parse(e.end),
                    weekly = e.weekly,
                    skipped = e.skipped.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
                    remindBefore = e.remindBefore,
                    note = e.note,
                )
            }.getOrNull()
        },
        deadlines = dto.deadlines.mapNotNull { d ->
            runCatching { Deadline(title = d.title, date = LocalDate.parse(d.date), note = d.note, id = d.id) }.getOrNull()
        },
        money = dto.money.mapNotNull { m ->
            runCatching {
                MoneyEntry(
                    id = m.id,
                    date = LocalDate.parse(m.date),
                    amount = m.amount,
                    income = m.income,
                    category = m.category,
                    note = m.note,
                    createdAt = m.createdAt,
                )
            }.getOrNull()?.takeIf { it.amount > 0 }
        },
        seeded = dto.seeded.toSet(),
    )

    fun encode(diary: Diary): String = json.encodeToString(DiaryDto.serializer(), toDto(diary))

    fun decode(text: String): Diary? =
        runCatching { fromDto(json.decodeFromString(DiaryDto.serializer(), text)) }.getOrNull()
}

/**
 * Общий на всё приложение дневник владельца телефона: им пользуются и экраны,
 * и напоминания, поэтому они всегда видят одно и то же. Хранится в файле diary.json.
 */
object DiaryRepository {

    private val lock = Any()
    private var file: File? = null
    private val mutableState = MutableStateFlow(Diary())

    val state: StateFlow<Diary> = mutableState.asStateFlow()

    fun init(filesDir: File) {
        synchronized(lock) {
            if (file == null) {
                val target = File(filesDir, FILE)
                file = target
                mutableState.value = if (target.exists()) DiaryCodec.decode(target.readText()) ?: Diary() else Diary()
            }
        }
    }

    fun current(filesDir: File): Diary {
        init(filesDir)
        return mutableState.value
    }

    /** Изменить дневник и сразу сохранить. */
    fun update(filesDir: File, change: (Diary) -> Diary) {
        synchronized(lock) {
            init(filesDir)
            val before = mutableState.value
            val after = change(before)
            if (after == before) return
            write(File(filesDir, FILE), after)
            mutableState.value = after
        }
    }

    /** Добавить записи из резервной копии. Возвращает, сколько новых записей появилось. */
    fun merge(filesDir: File, incoming: Diary): Int {
        var added = 0
        update(filesDir) { current ->
            val merged = current.mergeWith(incoming)
            added = merged.size - current.size
            merged
        }
        return added
    }

    private fun write(target: File, diary: Diary) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$FILE.tmp")
        temp.writeText(DiaryCodec.encode(diary))
        if (!temp.renameTo(target)) {
            target.writeText(DiaryCodec.encode(diary))
            temp.delete()
        }
    }

    private const val FILE = "diary.json"
}
