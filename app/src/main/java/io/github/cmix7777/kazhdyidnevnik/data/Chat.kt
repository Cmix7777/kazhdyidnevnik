package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Сообщение переписки. У каждого человека свои номера [seq]: 1, 2, 3…, поэтому «кто + номер» однозначно
 * называет сообщение, а повторная отправка не создаёт дубль.
 */
@Serializable
data class ChatMessage(
    @SerialName("f") val from: String,
    @SerialName("s") val seq: Long,
    /** Когда написано: по часам отправителя, но не раньше последнего сообщения переписки. */
    @SerialName("a") val at: Long,
    @SerialName("t") val text: String = "",
    /** Ответ на сообщение, например «aizat:12». */
    @SerialName("r") val replyTo: String? = null,
    @SerialName("d") val deleted: Boolean = false,
    @SerialName("p") val photo: ChatPhoto? = null,
    /** Эффект на всю переписку, когда сообщение появляется: hearts, night, sun, fire. */
    @SerialName("e") val effect: String? = null,
    /** Невидимые чернила: текст под блёстками, пока его не сотрёшь пальцем. */
    @SerialName("x") val secret: Boolean = false,
    /** Записка почерком отправителя. */
    @SerialName("w") val hand: Boolean = false,
    @SerialName("v") val voice: ChatVoice? = null,
    @SerialName("c") val circle: ChatCircle? = null,
    @SerialName("k") val sticker: ChatSticker? = null,
    /** Любой файл: документ, архив, таблица. */
    @SerialName("fl") val file: ChatFile? = null,
    /** Когда текст исправили (null — не исправляли). */
    @SerialName("ed") val edited: Long? = null,
) {
    val key: String get() = "$from:$seq"

    /** Удалённое сообщение: текст и вложения стираются, остаётся только место в переписке. */
    fun asDeleted(): ChatMessage = copy(
        text = "", photo = null, replyTo = null, deleted = true,
        effect = null, secret = false, hand = false, voice = null, circle = null, sticker = null, file = null,
    )

    /** Файлы сообщения в облаке: номер и сколько у него частей. */
    val files: List<Pair<String, Int>>
        get() = buildList {
            photo?.let { add(it.id to 1) }
            voice?.let { add(it.id to it.parts) }
            circle?.let { add(it.id to it.parts) }
            sticker?.let { add(it.id to 1) }
            file?.let { add(it.id to it.parts) }
        }

    /** Есть ли во что-то кроме текста (фото, голос, кружочек, стикер). */
    val hasMedia: Boolean get() = photo != null || voice != null || circle != null || sticker != null || file != null
}

/** Голосовое: звук лежит отдельным файлом (если длинный — несколькими частями). [wave] — громкость по кусочкам, 0..9. */
@Serializable
data class ChatVoice(
    @SerialName("i") val id: String,
    @SerialName("ms") val durationMs: Long,
    @SerialName("n") val parts: Int = 1,
    @SerialName("wf") val wave: String = "",
)

/** Кружочек: короткое видео с фронтальной камеры. */
@Serializable
data class ChatCircle(
    @SerialName("i") val id: String,
    @SerialName("ms") val durationMs: Long,
    @SerialName("n") val parts: Int = 1,
)

/** Файл: имя, размер и тип, сам он лежит в облаке частями до доставки. */
@Serializable
data class ChatFile(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
    @SerialName("b") val size: Long,
    @SerialName("m") val mime: String = "",
    @SerialName("p") val parts: Int = 1,
)

/** Стикер: картинка без пузыря. */
@Serializable
data class ChatSticker(
    @SerialName("i") val id: String,
)

/** Фото в сообщении: само оно лежит отдельным документом (зашифрованное), здесь только номер и размеры. */
@Serializable
data class ChatPhoto(
    @SerialName("i") val id: String,
    @SerialName("w") val width: Int,
    @SerialName("h") val height: Int,
)

/**
 * Голова переписки одного человека в облаке: последние его сообщения подряд, по возрастанию номера.
 * Когда их становится много, старые уходят в архивные страницы ([ChatPage]), а голова начинается заново.
 */
@Serializable
data class ChatHead(
    @SerialName("v") val format: Int = 1,
    @SerialName("f") val from: String,
    /** Сколько архивных страниц уже записано. */
    @SerialName("p") val pages: Int = 0,
    /** Номер последнего сообщения, 0 — ещё ни одного. */
    @SerialName("l") val last: Long = 0,
    @SerialName("m") val messages: List<ChatMessage> = emptyList(),
    /** Номера удалённых сообщений из архива: второй телефон узнаёт о них, не перечитывая архив. */
    @SerialName("d") val deletedInPages: List<Long> = emptyList(),
    /** Куда слать пуш этому телефону (токен Firebase). */
    @SerialName("push") val push: String? = null,
    /** Ссылка на бесплатную пересылку пушей (Google Apps Script). */
    @SerialName("hook") val hook: String? = null,
    /** Реакции этого человека: ключ сообщения → смайлик. */
    @SerialName("rx") val reactions: Map<String, String> = emptyMap(),
    /** Его почерк для записок (номер файла). */
    @SerialName("hf") val font: String? = null,
) {
    /** Номер первого сообщения головы. */
    val first: Long get() = messages.firstOrNull()?.seq ?: (last + 1)
}

/** Архивная страница переписки: сообщения подряд, начиная с номера первого из них. */
@Serializable
data class ChatPage(
    @SerialName("v") val format: Int = 1,
    @SerialName("f") val from: String,
    @SerialName("i") val index: Int,
    @SerialName("m") val messages: List<ChatMessage> = emptyList(),
)

/** Короткие отметки рядом с головой: версия головы, «прочитано до» и «печатает» (0 — не печатает). */
data class ChatMark(val version: Long, val read: Long, val typing: Long)

/** Короткая копия нового сообщения, которая едет в самом пуше, чтобы уведомление показалось сразу. */
@Serializable
data class ChatPreview(
    @SerialName("s") val seq: Long,
    @SerialName("a") val at: Long,
    @SerialName("t") val text: String = "",
    @SerialName("p") val photo: Boolean = false,
    /** Что за сообщение без текста: x — секрет, v — голосовое, c — кружочек, k — стикер, w — записка. */
    @SerialName("k") val kind: String? = null,
)

/** Переписка на этом телефоне: свои сообщения, сообщения второго человека и служебные отметки. */
@Serializable
data class ChatState(
    /** Отпечаток кода связи: если код сменился, переписка начинается заново, а прежняя остаётся выше. */
    val pair: String = "",
    val mine: List<ChatMessage> = emptyList(),
    val theirs: List<ChatMessage> = emptyList(),
    /** Сообщения прошлой связи (по старому коду): только для чтения. */
    val earlier: List<ChatMessage> = emptyList(),
    /** Свою переписку в облаке уже сверили (после установки или нового кода). Пока нет — писать в облако нельзя. */
    val restored: Boolean = false,
    /** Номер первого сообщения моей головы. */
    val headStart: Long = 1,
    /** Номера первых сообщений моих архивных страниц. */
    val pageStarts: List<Long> = emptyList(),
    /** Мои архивные страницы, которые нужно переписать (там удалили сообщение). */
    val dirtyPages: List<Int> = emptyList(),
    val deletedInPages: List<Long> = emptyList(),
    /** Счётчик своих изменений и до какого из них облако уже знает. */
    val revision: Long = 0,
    val uploadedRevision: Long = 0,
    /** До какого номера мои сообщения точно в облаке. */
    val uploaded: Long = 0,
    /** Фото, которые ещё не ушли в облако, и фото, которые надо стереть там. */
    val pendingFiles: List<String> = emptyList(),
    val wipeFiles: List<String> = emptyList(),
    val myVersion: Long = 0,
    val theirVersion: Long = 0,
    /** До какого номера я прочитал сообщения второго человека и какую отметку уже отправил. */
    val readUpTo: Long = 0,
    val readSent: Long = 0,
    /** До какого номера второй человек прочитал мои. */
    val theirRead: Long = 0,
    /** До какого номера уже было уведомление. */
    val notifiedUpTo: Long = 0,
    val theirPush: String? = null,
    val theirHook: String? = null,
    /** Какой свой токен пушей и какую ссылку пересылки уже положил в голову. */
    val pushPublished: String? = null,
    val hookPublished: String? = null,
    /** Мои реакции на сообщения (ключ → смайлик) и реакции второго человека. */
    val myReactions: Map<String, String> = emptyMap(),
    val theirReactions: Map<String, String> = emptyMap(),
    /** Секреты под невидимыми чернилами, уже проявленные на этом телефоне. */
    val opened: List<String> = emptyList(),
    /** Мой почерк (номер файла в облаке) и почерк второго человека. */
    val myFont: String? = null,
    val theirFont: String? = null,
    /** Сообщения, эффект которых уже сыграл: при следующем открытии он не повторяется. */
    val played: List<String> = emptyList(),
    /** Сообщения «в будущее»: лежат на этом телефоне и уходят в назначенное время. */
    val scheduled: List<ScheduledMessage> = emptyList(),
)

/** Сообщение, которое уйдёт само в [at] (миллисекунды). До этого его видно только отправителю. */
@Serializable
data class ScheduledMessage(
    @SerialName("i") val id: String,
    @SerialName("t") val text: String,
    @SerialName("at") val at: Long,
    @SerialName("r") val replyTo: String? = null,
)

/** Как отмечено своё сообщение: ещё в телефоне, ушло, прочитано. */
enum class ChatTick { NONE, PENDING, SENT, READ }

/** Строка ленты переписки. */
sealed interface ChatRow {
    val key: String

    /** [tag] отличает повтор того же дня (прежняя переписка и нынешняя), чтобы ключи строк не совпадали. */
    data class Day(val date: LocalDate, val tag: String = "") : ChatRow {
        override val key: String get() = "day-$date$tag"
    }

    data class Message(
        val message: ChatMessage,
        val mine: Boolean,
        /** Первое и последнее сообщение подряд от одного человека (у них скругления другие). */
        val first: Boolean,
        val last: Boolean,
        val tick: ChatTick,
        /** На что это ответ (если сообщение ещё есть). */
        val reply: ChatMessage?,
        val earlier: Boolean = false,
        val myReaction: String? = null,
        val theirReaction: String? = null,
    ) : ChatRow {
        override val key: String get() = (if (earlier) "old-" else "") + message.key + "-" + message.at
    }
}

/** Вся логика переписки без сети и Android: её проверяют обычные тесты. */
object ChatLogic {

    const val MAX_TEXT = 4000

    /**
     * Голова в облаке не больше этого (в байтах JSON до шифрования). Правила базы пускают до 300 000 знаков,
     * но голову скачивают при каждом новом сообщении, поэтому она маленькая: обычно 10–15 КБ.
     */
    const val HEAD_LIMIT_BYTES = 60_000
    const val HEAD_LIMIT_COUNT = 100

    /** Сколько последних удалений из архива держать в голове. */
    const val KEEP_DELETED = 400

    /** Сколько последних реакций держать в голове. */
    const val KEEP_REACTIONS = 300

    /** Сколько отметок «секрет проявлен» и «эффект сыграл» помнить. */
    const val KEEP_MARKS = 300

    /** Часть файла в облаке: в зашифрованном виде проходит правила базы. */
    const val FILE_PART_BYTES = 215_000

    /** Номера частей файла: первая под самим номером, дальше «номер-1», «номер-2»… */
    fun partIds(id: String, parts: Int): List<String> = (0 until parts.coerceAtLeast(1)).map { if (it == 0) id else "$id-$it" }

    fun partsFor(bytes: Int): Int = ((bytes + FILE_PART_BYTES - 1) / FILE_PART_BYTES).coerceAtLeast(1)

    /** Сообщения подряд от одного человека группируются, если между ними меньше этого. */
    private const val GROUP_MS = 5 * 60_000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun encodeState(state: ChatState): String = json.encodeToString(ChatState.serializer(), state)

    fun decodeState(text: String): ChatState? = runCatching { json.decodeFromString(ChatState.serializer(), text) }.getOrNull()

    fun encodeHead(head: ChatHead): String = json.encodeToString(ChatHead.serializer(), head)

    fun decodeHead(text: String): ChatHead? = runCatching { json.decodeFromString(ChatHead.serializer(), text) }.getOrNull()

    fun encodePage(page: ChatPage): String = json.encodeToString(ChatPage.serializer(), page)

    fun decodePage(text: String): ChatPage? = runCatching { json.decodeFromString(ChatPage.serializer(), text) }.getOrNull()

    fun encodePreview(preview: ChatPreview): String = json.encodeToString(ChatPreview.serializer(), preview)

    fun decodePreview(text: String): ChatPreview? = runCatching { json.decodeFromString(ChatPreview.serializer(), text) }.getOrNull()

    fun encodedSize(messages: List<ChatMessage>): Int =
        json.encodeToString(ListSerializer(ChatMessage.serializer()), messages).toByteArray(Charsets.UTF_8).size

    fun lastSeq(messages: List<ChatMessage>): Long = messages.lastOrNull()?.seq ?: 0L

    /** Время нового сообщения: сейчас, но не раньше последнего в переписке (часы телефонов могут расходиться). */
    fun sendTime(state: ChatState, now: Long): Long {
        val latest = maxOf(state.mine.lastOrNull()?.at ?: 0L, state.theirs.maxOfOrNull { it.at } ?: 0L)
        return maxOf(now, latest + 1)
    }

    /** Добавить своё сообщение. Пустое без фото не добавляется. */
    fun append(
        state: ChatState,
        me: Person,
        text: String,
        now: Long,
        replyTo: String? = null,
        photo: ChatPhoto? = null,
        effect: String? = null,
        secret: Boolean = false,
        hand: Boolean = false,
        voice: ChatVoice? = null,
        circle: ChatCircle? = null,
        sticker: ChatSticker? = null,
        file: ChatFile? = null,
    ): ChatState {
        val clean = text.trim().take(MAX_TEXT)
        val media = photo != null || voice != null || circle != null || sticker != null || file != null
        if (clean.isEmpty() && !media) return state
        val message = ChatMessage(
            from = me.id,
            seq = lastSeq(state.mine) + 1,
            at = sendTime(state, now),
            text = clean,
            replyTo = replyTo,
            photo = photo,
            effect = effect,
            secret = secret && clean.isNotEmpty(),
            hand = hand && clean.isNotEmpty(),
            voice = voice,
            circle = circle,
            sticker = sticker,
            file = file,
        )
        return state.copy(
            mine = state.mine + message,
            revision = state.revision + 1,
            pendingFiles = state.pendingFiles + message.files.map { it.first },
            // Свой эффект уже сыграл при отправке.
            played = if (effect != null) (state.played + message.key).takeLast(KEEP_MARKS) else state.played,
        )
    }

    /** Поставить или снять свою реакцию. Тот же смайлик второй раз снимает её. */
    fun react(state: ChatState, key: String, emoji: String?): ChatState {
        val next = if (emoji == null || state.myReactions[key] == emoji) null else emoji
        if (next == null && key !in state.myReactions) return state
        val map = LinkedHashMap(state.myReactions)
        map.remove(key)
        if (next != null) map[key] = next
        val trimmed = if (map.size > KEEP_REACTIONS) map.entries.toList().takeLast(KEEP_REACTIONS).associate { it.key to it.value } else map
        return state.copy(myReactions = trimmed, revision = state.revision + 1)
    }

    /** Секрет проявлен на этом телефоне. */
    fun open(state: ChatState, key: String): ChatState =
        if (key in state.opened) state else state.copy(opened = (state.opened + key).takeLast(KEEP_MARKS))

    /** Эффект сообщения сыграл. */
    fun played(state: ChatState, key: String): ChatState =
        if (key in state.played) state else state.copy(played = (state.played + key).takeLast(KEEP_MARKS))

    /** Новый почерк: старый файл стирается в облаке, новый уходит туда. */
    fun newFont(state: ChatState, id: String): ChatState {
        val old = state.myFont
        val oldPending = old != null && old in state.pendingFiles
        return state.copy(
            myFont = id,
            revision = state.revision + 1,
            pendingFiles = (if (old != null && oldPending) state.pendingFiles - old else state.pendingFiles) + id,
            wipeFiles = if (old != null && !oldPending) state.wipeFiles + old else state.wipeFiles,
        )
    }

    /** Эффект по словам: «люблю» — сердечки, «спокойной ночи» — звёзды и так далее. */
    fun detectEffect(text: String): String? {
        val t = " " + text.lowercase().replace('ё', 'е') + " "
        fun has(word: String) = Regex("[^а-яa-z]$word[^а-яa-z]").containsMatchIn(t)
        return when {
            "спокойной ночи" in t || "сладких снов" in t || has("споки") -> "night"
            "доброе утро" in t || "доброго утра" in t || has("утречко") -> "sun"
            "люблю" in t -> "hearts"
            "с днем рождения" in t || "с праздником" in t || has("с др") || Regex("[^а-я]ура+[^а-я]").containsMatchIn(t) -> "fire"
            else -> null
        }
    }

    /** Удалить своё сообщение у обоих. */
    fun deleteMine(state: ChatState, seq: Long): ChatState {
        val target = state.mine.firstOrNull { it.seq == seq } ?: return state
        if (target.deleted) return state
        val inPage = seq < state.headStart && state.pageStarts.isNotEmpty()
        val page = if (inPage) state.pageStarts.indexOfLast { it <= seq }.takeIf { it >= 0 } else null
        val files = target.files
        val notUploaded = files.filter { it.first in state.pendingFiles }
        val toWipe = (files - notUploaded.toSet()).flatMap { (id, parts) -> partIds(id, parts) }
        return state.copy(
            mine = state.mine.map { if (it.seq == seq) it.asDeleted() else it },
            revision = state.revision + 1,
            dirtyPages = if (page != null) (state.dirtyPages + page).distinct() else state.dirtyPages,
            deletedInPages = if (page != null) (state.deletedInPages + seq).takeLast(KEEP_DELETED) else state.deletedInPages,
            pendingFiles = state.pendingFiles - notUploaded.map { it.first }.toSet(),
            wipeFiles = state.wipeFiles + toWipe,
            myReactions = state.myReactions - target.key,
        )
    }

    /**
     * Можно ли исправить своё сообщение: текст или подпись к фото, не удалённое и ещё в голове переписки
     * (её второй телефон перечитывает при каждом изменении; старые архивные страницы он не перечитывает).
     */
    fun canEdit(state: ChatState, message: ChatMessage): Boolean =
        !message.deleted && message.seq >= state.headStart && state.mine.any { it.seq == message.seq && it.at == message.at } &&
            message.voice == null && message.circle == null && message.sticker == null && message.file == null &&
            (message.text.isNotEmpty() || message.photo != null)

    /** Исправить текст своего сообщения. Пустой текст у фото — фото без подписи; пустое сообщение не бывает. */
    fun editMine(state: ChatState, seq: Long, text: String, now: Long): ChatState {
        val target = state.mine.firstOrNull { it.seq == seq } ?: return state
        if (!canEdit(state, target)) return state
        val clean = text.trim().take(MAX_TEXT)
        if (clean == target.text || (clean.isEmpty() && target.photo == null)) return state
        return state.copy(
            mine = state.mine.map { if (it.seq == seq) it.copy(text = clean, edited = now) else it },
            revision = state.revision + 1,
        )
    }

    /** Отложить сообщение до [at]: до этого времени оно лежит на телефоне и видно только отправителю. */
    fun schedule(state: ChatState, id: String, text: String, at: Long, replyTo: String? = null): ChatState {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isEmpty() || state.scheduled.any { it.id == id }) return state
        return state.copy(scheduled = (state.scheduled + ScheduledMessage(id, clean, at, replyTo)).sortedBy { it.at })
    }

    fun unschedule(state: ChatState, id: String): ChatState = state.copy(scheduled = state.scheduled.filter { it.id != id })

    /** Отложенные сообщения, время которых пришло, уходят как обычные (с нынешним временем, по порядку). */
    fun releaseDue(state: ChatState, me: Person, now: Long): ChatState {
        val due = state.scheduled.filter { it.at <= now }
        if (due.isEmpty()) return state
        var next = state.copy(scheduled = state.scheduled - due.toSet())
        due.forEachIndexed { index, item ->
            val replyTo = item.replyTo?.takeIf { key -> (next.mine + next.theirs).any { it.key == key && !it.deleted } }
            next = append(next, me, item.text, now + index, replyTo)
        }
        return next
    }

    /** Есть ли что отправить в облако (сообщения, удаления, токен пушей). */
    fun hasPending(state: ChatState, push: String?, hook: String?): Boolean =
        needsHead(state, push, hook) || state.pendingFiles.isNotEmpty() || state.wipeFiles.isNotEmpty()

    fun needsHead(state: ChatState, push: String?, hook: String?): Boolean =
        state.revision > state.uploadedRevision || state.dirtyPages.isNotEmpty() ||
            push != state.pushPublished || hook != state.hookPublished

    /** Что записать в облако одним махом. */
    data class Upload(val pages: List<ChatPage>, val head: ChatHead, val headStart: Long, val pageStarts: List<Long>)

    /**
     * Собрать голову и страницы для отправки. Если голова переросла, все её сообщения, кроме самого
     * нового, уходят в новую архивную страницу.
     */
    fun planUpload(
        state: ChatState,
        me: Person,
        push: String?,
        hook: String?,
        sizeOf: (List<ChatMessage>) -> Int = { encodedSize(it) },
    ): Upload {
        var headStart = state.headStart
        var pageStarts = state.pageStarts
        val dirty = state.dirtyPages.toMutableSet()
        var headMessages = state.mine.filter { it.seq >= headStart }
        if (headMessages.size > 1 && (headMessages.size > HEAD_LIMIT_COUNT || sizeOf(headMessages) > HEAD_LIMIT_BYTES)) {
            val newest = headMessages.last()
            pageStarts = pageStarts + headStart
            dirty += pageStarts.lastIndex
            headStart = newest.seq
            headMessages = listOf(newest)
        }
        val pages = dirty.filter { it in pageStarts.indices }.sorted().map { index ->
            val start = pageStarts[index]
            val end = if (index + 1 < pageStarts.size) pageStarts[index + 1] else headStart
            ChatPage(from = me.id, index = index, messages = state.mine.filter { it.seq in start until end })
        }
        val head = ChatHead(
            from = me.id,
            pages = pageStarts.size,
            last = lastSeq(state.mine),
            messages = headMessages,
            deletedInPages = state.deletedInPages.takeLast(KEEP_DELETED),
            push = push,
            hook = hook,
            reactions = state.myReactions,
            font = state.myFont,
        )
        return Upload(pages, head, headStart, pageStarts)
    }

    /**
     * Отправка [plan] удалась. [current] — состояние сейчас (пока шла отправка, могли написать ещё),
     * [revision] — счётчик изменений на момент сборки.
     */
    fun afterUpload(current: ChatState, plan: Upload, version: Long, revision: Long): ChatState = current.copy(
        uploaded = maxOf(current.uploaded, plan.head.last),
        headStart = plan.headStart,
        pageStarts = plan.pageStarts,
        dirtyPages = current.dirtyPages.filter { index -> plan.pages.none { it.index == index } },
        uploadedRevision = maxOf(current.uploadedRevision, revision),
        myVersion = version,
        pushPublished = plan.head.push,
        hookPublished = plan.head.hook,
    )

    /**
     * Своя переписка нашлась в облаке (после переустановки или на новом телефоне): забираем её.
     * Сообщения, написанные здесь до сверки, получают номера после облачных.
     */
    fun restoreMine(state: ChatState, head: ChatHead, pages: List<ChatPage>, mark: ChatMark): ChatState {
        val remote = (pages.sortedBy { it.index }.flatMap { it.messages } + head.messages)
            .distinctBy { it.seq }
            .sortedBy { it.seq }
        val local = state.mine.mapIndexed { index, message -> message.copy(seq = head.last + 1 + index) }
        // Архив должен быть прочитан целиком (это проверяет вызывающий), иначе новая страница затёрла бы старую.
        val pageStarts = pages.sortedBy { it.index }.mapNotNull { it.messages.firstOrNull()?.seq }
        return state.copy(
            mine = remote + local,
            restored = true,
            headStart = head.first,
            pageStarts = pageStarts,
            dirtyPages = emptyList(),
            deletedInPages = head.deletedInPages,
            uploaded = head.last,
            revision = if (local.isEmpty()) state.revision else maxOf(state.revision, state.uploadedRevision + 1),
            uploadedRevision = if (local.isEmpty()) state.revision else state.uploadedRevision,
            myVersion = mark.version,
            readUpTo = maxOf(state.readUpTo, mark.read),
            readSent = mark.read,
            notifiedUpTo = maxOf(state.notifiedUpTo, mark.read),
            pushPublished = head.push,
            hookPublished = head.hook,
            myReactions = head.reactions,
            myFont = head.font ?: state.myFont,
        )
    }

    /**
     * Итог чтения головы второго человека. [missingFrom] — с какого номера нужен архив (сообщения пропущены).
     * [restarted] — второй телефон начал нумерацию заново, отметки «прочитано» опущены.
     */
    data class Merge(val state: ChatState, val missingFrom: Long?, val restarted: Boolean = false)

    /**
     * Второй телефон начал нумерацию заново (переустановка, сброс): его голова кончается раньше, чем то, что уже есть.
     * Сообщения с большими номерами — из прежней нумерации, они уходят в «раньше». Отметки «прочитано»
     * пересчитываются по времени: прочитано то, что написано не позже последнего прочитанного раньше.
     * Иначе каждое новое сообщение сразу считалось бы прочитанным: у него стояло бы «прочитано»,
     * а здесь не было бы ни уведомления, ни счётчика.
     */
    private fun afterRestart(state: ChatState, head: ChatHead): ChatState? {
        if (head.last >= lastSeq(state.theirs)) return null
        val stale = state.theirs.filter { it.seq > head.last }
        // Новые сообщения из головы, написанные не позже [limit], — уже виденные.
        fun upTo(mark: Long): Long {
            val limit = state.theirs.filter { it.seq <= mark }.maxOfOrNull { it.at } ?: return 0
            val seen = head.messages.filter { it.at <= limit }.maxOfOrNull { it.seq }
            return seen ?: if (head.messages.isEmpty()) head.last else (head.first - 1).coerceAtLeast(0)
        }
        return state.copy(
            theirs = state.theirs.filter { it.seq <= head.last },
            earlier = (state.earlier + stale.filter { !it.deleted }).distinctBy { Triple(it.from, it.seq, it.at) }.sortedBy { it.at },
            readUpTo = minOf(state.readUpTo, upTo(state.readUpTo)),
            notifiedUpTo = minOf(state.notifiedUpTo, upTo(state.notifiedUpTo)),
            // Опущенную отметку надо отправить заново, даже если она меньше уже отправленной.
            readSent = -1,
        )
    }

    fun mergeTheirs(state: ChatState, head: ChatHead, version: Long): Merge {
        val reset = afterRestart(state, head)
        val base = reset ?: state
        val known = lastSeq(base.theirs)
        val merged = mergeMessages(base, head.from, head.messages)
        val withDeletes = head.deletedInPages.toSet().let { gone ->
            if (gone.isEmpty()) merged else merged.copy(theirs = merged.theirs.map { if (it.seq in gone && !it.deleted) it.asDeleted() else it })
        }
        val missing = if (head.first > known + 1 && head.pages > 0) known + 1 else null
        return Merge(
            withDeletes.copy(
                theirVersion = version,
                theirPush = head.push,
                theirHook = head.hook ?: state.theirHook,
                theirReactions = head.reactions,
                theirFont = head.font ?: state.theirFont,
            ),
            missing,
            restarted = reset != null,
        )
    }

    fun mergeTheirsPage(state: ChatState, page: ChatPage): ChatState = mergeMessages(state, page.from, page.messages)

    private fun mergeMessages(state: ChatState, from: String, incoming: List<ChatMessage>): ChatState {
        if (incoming.isEmpty()) return state
        val bySeq = state.theirs.associateByTo(LinkedHashMap()) { it.seq }
        val moved = mutableListOf<ChatMessage>()
        for (message in incoming) {
            if (message.from != from) continue
            val old = bySeq[message.seq]
            bySeq[message.seq] = when {
                old == null -> message
                old.deleted || message.deleted -> message.asDeleted().copy(at = old.at)
                // Тот же номер, но другое сообщение: второй телефон начал переписку заново. Прежнее не теряем.
                // Остаётся более новое: старая страница архива не должна вытеснять свежее сообщение из головы
                // (иначе они менялись бы местами при каждой сверке и копились в «раньше»).
                old.at != message.at -> {
                    if (message.at >= old.at) {
                        moved += old
                        message
                    } else {
                        moved += message
                        old
                    }
                }
                else -> message
            }
        }
        if (moved.isEmpty()) return state.copy(theirs = bySeq.values.sortedBy { it.seq })
        return state.copy(
            theirs = bySeq.values.sortedBy { it.seq },
            earlier = (state.earlier + moved).distinctBy { Triple(it.from, it.seq, it.at) }.sortedBy { it.at },
        )
    }

    /** Код связи сменился: переписка начинается заново, прежняя остаётся выше только для чтения. */
    fun repair(state: ChatState, tag: String): ChatState {
        if (state.pair == tag) return state
        if (state.pair.isEmpty() && state.mine.isEmpty() && state.theirs.isEmpty()) return state.copy(pair = tag)
        val old = (state.earlier + state.mine + state.theirs).filter { !it.deleted }.sortedBy { it.at }
        return ChatState(pair = tag, earlier = old)
    }

    fun unread(state: ChatState): Int = state.theirs.count { !it.deleted && it.seq > state.readUpTo }

    /** Новые сообщения второго человека, о которых ещё не было уведомления. */
    fun toNotify(state: ChatState): List<ChatMessage> =
        state.theirs.filter { !it.deleted && it.seq > state.readUpTo && it.seq > state.notifiedUpTo }

    /** Все непрочитанные (для уведомления целиком). */
    fun unreadMessages(state: ChatState): List<ChatMessage> =
        state.theirs.filter { !it.deleted && it.seq > state.readUpTo }

    fun tick(state: ChatState, message: ChatMessage, mine: Boolean): ChatTick = when {
        !mine -> ChatTick.NONE
        message.seq > state.uploaded -> ChatTick.PENDING
        message.seq <= state.theirRead -> ChatTick.READ
        else -> ChatTick.SENT
    }

    /** Найти сообщение по ключу «кто:номер». */
    fun find(state: ChatState, key: String?): ChatMessage? {
        if (key == null) return null
        val from = key.substringBefore(':')
        val seq = key.substringAfter(':').toLongOrNull() ?: return null
        return state.mine.firstOrNull { it.from == from && it.seq == seq }
            ?: state.theirs.firstOrNull { it.from == from && it.seq == seq }
    }

    /** Текст для уведомления и ответа: фото подписывается словом. */
    fun shortText(message: ChatMessage): String = when {
        message.deleted -> "Сообщение удалено"
        message.secret -> "Секрет под невидимыми чернилами"
        message.voice != null -> "Голосовое"
        message.circle != null -> "Кружочек"
        message.sticker != null -> "Стикер"
        message.file != null -> "Файл: ${message.file.name}"
        message.photo != null && message.text.isNotBlank() -> "Фото: ${message.text}"
        message.photo != null -> "Фото"
        message.hand -> "Записка: ${message.text}"
        else -> message.text
    }

    /** Вид сообщения для пуша (текст секрета в пуш не попадает). */
    fun kindOf(message: ChatMessage): String? = when {
        message.secret -> "x"
        message.voice != null -> "v"
        message.circle != null -> "c"
        message.sticker != null -> "k"
        message.file != null -> "f"
        message.hand -> "w"
        else -> null
    }

    /** Сообщение из короткой копии в пуше: только чтобы показать уведомление. */
    fun fromPreview(from: String, preview: ChatPreview): ChatMessage = ChatMessage(
        from = from,
        seq = preview.seq,
        at = preview.at,
        text = preview.text,
        photo = if (preview.photo) ChatPhoto("", 1, 1) else null,
        secret = preview.kind == "x",
        hand = preview.kind == "w",
        voice = if (preview.kind == "v") ChatVoice("", 0) else null,
        circle = if (preview.kind == "c") ChatCircle("", 0) else null,
        sticker = if (preview.kind == "k") ChatSticker("") else null,
        file = if (preview.kind == "f") ChatFile("", preview.text, 0) else null,
    )

    /** Копия для пуша: не длиннее [maxBytes] байт текста, чтобы пуш уложился в 4 КБ. */
    fun preview(message: ChatMessage, maxBytes: Int = 1500): ChatPreview {
        var text = if (message.secret) "" else message.file?.name ?: message.text
        while (text.toByteArray(Charsets.UTF_8).size > maxBytes) text = text.dropLast(maxOf(1, text.length / 10))
        val full = message.file?.name ?: message.text
        if (!message.secret && text.length < full.length) text = text.trimEnd() + "…"
        return ChatPreview(seq = message.seq, at = message.at, text = text, photo = message.photo != null, kind = kindOf(message))
    }

    /**
     * Лента для экрана, от новых к старым (список рисуется снизу вверх): сообщения, разделители дней,
     * группы подряд от одного человека и отметки «ушло» и «прочитано».
     */
    fun rows(state: ChatState, me: Person, zone: ZoneId = ZoneId.systemDefault()): List<ChatRow> {
        data class Item(val message: ChatMessage, val mine: Boolean, val earlier: Boolean)
        // Удалённые сообщения исчезают совсем, как будто их и не было.
        // Одно и то же сообщение дважды (в прежних версиях оно могло попасть в «раньше» не один раз) — показываем однажды.
        val items = buildList {
            state.earlier.forEach { if (!it.deleted) add(Item(it, it.from == me.id, true)) }
            state.mine.forEach { if (!it.deleted) add(Item(it, true, false)) }
            state.theirs.forEach { if (!it.deleted) add(Item(it, false, false)) }
        }.distinctBy { Triple(it.earlier, it.message.key, it.message.at) }
            .sortedWith(compareBy<Item>({ !it.earlier }, { it.message.at }, { it.message.from }, { it.message.seq }))
        val byKey = HashMap<String, ChatMessage>(state.mine.size + state.theirs.size)
        state.mine.forEach { if (!it.deleted) byKey[it.key] = it }
        state.theirs.forEach { if (!it.deleted) byKey[it.key] = it }

        val out = ArrayList<ChatRow>(items.size + 16)
        // Ключи строк обязаны быть разными, иначе список падает при открытии переписки.
        val usedDays = HashMap<String, Int>()
        var previousDay: LocalDate? = null
        items.forEachIndexed { index, item ->
            val day = Instant.ofEpochMilli(item.message.at).atZone(zone).toLocalDate()
            if (day != previousDay) {
                val base = (if (item.earlier) "-old" else "")
                val seen = usedDays.getOrDefault("$day$base", 0)
                usedDays["$day$base"] = seen + 1
                out += ChatRow.Day(day, base + (if (seen > 0) "-$seen" else ""))
                previousDay = day
            }
            val before = items.getOrNull(index - 1)
            val after = items.getOrNull(index + 1)
            fun sameGroup(other: Item?): Boolean = other != null && other.mine == item.mine &&
                other.earlier == item.earlier &&
                kotlin.math.abs(other.message.at - item.message.at) < GROUP_MS &&
                Instant.ofEpochMilli(other.message.at).atZone(zone).toLocalDate() == day
            out += ChatRow.Message(
                message = item.message,
                mine = item.mine,
                first = !sameGroup(before),
                last = !sameGroup(after),
                tick = if (item.earlier) ChatTick.NONE else tick(state, item.message, item.mine),
                reply = if (item.earlier) null else item.message.replyTo?.let { byKey[it] },
                earlier = item.earlier,
                myReaction = if (item.earlier) null else state.myReactions[item.message.key],
                theirReaction = if (item.earlier) null else state.theirReactions[item.message.key],
            )
        }
        return out.asReversed()
    }
}
