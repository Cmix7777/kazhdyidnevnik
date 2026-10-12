package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Дела одного человека, которыми он сам поделился с другим: от кого, когда и сами дела. */
data class SharedEvents(
    val from: Person,
    val at: Long,
    val events: List<UserEvent>,
    /** Особые даты (дни рождения, годовщины): они общие, поэтому уходят второму телефону без заметок. */
    val dates: List<Deadline> = emptyList(),
)

@Serializable
internal data class ShareDto(
    val v: Int = 1,
    val from: String,
    val at: Long,
    val events: List<EventDto> = emptyList(),
    val dates: List<DeadlineDto> = emptyList(),
)

/**
 * Обмен делами между двумя телефонами без сервера: человек нажимает «Поделиться делами»,
 * приложение собирает короткий код и отправляет его сообщением (в Telegram, в SMS — куда угодно).
 * Второй копирует сообщение и нажимает «Вставить». Ничего не уходит само: это снимок на момент отправки.
 * Заметки, напоминания, бюджет и важные даты не передаются.
 */
object PartnerShare {

    const val PREFIX = "KD1:"
    private val codeRegex = Regex("KD1:[A-Za-z0-9_-]+")
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_BYTES = 2_000_000

    /** Что стоит передавать: повторяющиеся дела и разовые, которые ещё не прошли (со вчерашнего дня). */
    fun relevant(events: List<UserEvent>, today: LocalDate): List<UserEvent> =
        events
            .filter { it.weekly || !it.date.isBefore(today.minusDays(1)) }
            .map { it.copy(note = "", remindBefore = null, skipped = it.skipped.filter { d -> !d.isBefore(today.minusDays(1)) }.toSet()) }

    /** Какие даты передавать: только особые, без заметок (в заметке может быть, например, идея подарка). */
    fun relevantDates(deadlines: List<Deadline>): List<Deadline> =
        deadlines.filter { it.special && it.id != null }.map { it.copy(note = null) }

    fun encode(shared: SharedEvents): String {
        val dto = DiaryCodec.toDto(Diary(events = shared.events, deadlines = shared.dates)).let { codec ->
            ShareDto(from = shared.from.id, at = shared.at, events = codec.events, dates = codec.deadlines)
        }
        val raw = json.encodeToString(ShareDto.serializer(), dto).toByteArray(Charsets.UTF_8)
        val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
    }

    /** Достаёт код из любого текста (сообщения целиком) и разбирает его. null — кода нет или он повреждён. */
    fun decode(text: String): SharedEvents? {
        val code = codeRegex.find(text)?.value ?: return null
        return runCatching {
            val packed = Base64.getUrlDecoder().decode(code.removePrefix(PREFIX))
            val raw = GZIPInputStream(ByteArrayInputStream(packed)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    check(out.size() <= MAX_BYTES)
                }
                out.toByteArray()
            }
            val dto = json.decodeFromString(ShareDto.serializer(), raw.toString(Charsets.UTF_8))
            val from = Person.fromId(dto.from) ?: return null
            val diary = DiaryCodec.fromDto(DiaryDto(events = dto.events, deadlines = dto.dates))
            SharedEvents(from, dto.at, diary.events, diary.deadlines.filter { it.special })
        }.getOrNull()
    }

    /** Сообщение, которое уходит в мессенджер: понятная подпись и код. */
    fun message(from: Person, shared: SharedEvents): String =
        "Мои дела из Каждогодневника (${from.shortName}). Скопируй всё сообщение и нажми " +
            "«Вставить дела» на моей странице в приложении.\n" + encode(shared)
}

/** Полученные от второго человека дела, сохранённые на этом телефоне (файл partner-events.txt). */
object PartnerEventsRepository {

    private val lock = Any()
    private var file: File? = null
    private val mutableState = MutableStateFlow<SharedEvents?>(null)

    val state: StateFlow<SharedEvents?> = mutableState.asStateFlow()

    fun init(filesDir: File) {
        synchronized(lock) {
            if (file == null) {
                val target = File(filesDir, FILE)
                file = target
                mutableState.value = if (target.exists()) runCatching { PartnerShare.decode(target.readText()) }.getOrNull() else null
            }
        }
    }

    fun current(filesDir: File): SharedEvents? {
        init(filesDir)
        return mutableState.value
    }

    fun save(filesDir: File, shared: SharedEvents) {
        synchronized(lock) {
            init(filesDir)
            File(filesDir, FILE).writeText(PartnerShare.encode(shared))
            mutableState.value = shared
        }
    }

    private const val FILE = "partner-events.txt"
}
