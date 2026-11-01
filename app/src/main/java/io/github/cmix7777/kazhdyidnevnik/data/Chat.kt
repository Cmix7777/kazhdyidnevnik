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
) {
    val key: String get() = "$from:$seq"

    /** Удалённое сообщение: текст и фото стираются, остаётся только место в переписке. */
    fun asDeleted(): ChatMessage = copy(text = "", photo = null, replyTo = null, deleted = true)
}

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
)

/** Как отмечено своё сообщение: ещё в телефоне, ушло, прочитано. */
enum class ChatTick { NONE, PENDING, SENT, READ }

/** Строка ленты переписки. */
sealed interface ChatRow {
    val key: String

    data class Day(val date: LocalDate) : ChatRow {
        override val key: String get() = "day-$date"
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
    ): ChatState {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isEmpty() && photo == null) return state
        val message = ChatMessage(
            from = me.id,
            seq = lastSeq(state.mine) + 1,
            at = sendTime(state, now),
            text = clean,
            replyTo = replyTo,
            photo = photo,
        )
        return state.copy(
            mine = state.mine + message,
            revision = state.revision + 1,
            pendingFiles = if (photo != null) state.pendingFiles + photo.id else state.pendingFiles,
        )
    }

    /** Удалить своё сообщение у обоих. */
    fun deleteMine(state: ChatState, seq: Long): ChatState {
        val target = state.mine.firstOrNull { it.seq == seq } ?: return state
        if (target.deleted) return state
        val inPage = seq < state.headStart && state.pageStarts.isNotEmpty()
        val page = if (inPage) state.pageStarts.indexOfLast { it <= seq }.takeIf { it >= 0 } else null
        val photo = target.photo?.id
        val notUploaded = photo != null && photo in state.pendingFiles
        return state.copy(
            mine = state.mine.map { if (it.seq == seq) it.asDeleted() else it },
            revision = state.revision + 1,
            dirtyPages = if (page != null) (state.dirtyPages + page).distinct() else state.dirtyPages,
            deletedInPages = if (page != null) (state.deletedInPages + seq).takeLast(KEEP_DELETED) else state.deletedInPages,
            pendingFiles = if (photo != null) state.pendingFiles - photo else state.pendingFiles,
            wipeFiles = if (photo != null && !notUploaded) state.wipeFiles + photo else state.wipeFiles,
        )
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
        )
    }

    /** Итог чтения головы второго человека. [missingFrom] — с какого номера нужен архив (сообщения пропущены). */
    data class Merge(val state: ChatState, val missingFrom: Long?)

    fun mergeTheirs(state: ChatState, head: ChatHead, version: Long): Merge {
        val known = lastSeq(state.theirs)
        val merged = mergeMessages(state, head.from, head.messages)
        val withDeletes = head.deletedInPages.toSet().let { gone ->
            if (gone.isEmpty()) merged else merged.copy(theirs = merged.theirs.map { if (it.seq in gone && !it.deleted) it.asDeleted() else it })
        }
        val missing = if (head.first > known + 1 && head.pages > 0) known + 1 else null
        return Merge(
            withDeletes.copy(theirVersion = version, theirPush = head.push, theirHook = head.hook ?: state.theirHook),
            missing,
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
                old.at != message.at -> {
                    moved += old
                    message
                }
                else -> message
            }
        }
        return state.copy(
            theirs = bySeq.values.sortedBy { it.seq },
            earlier = if (moved.isEmpty()) state.earlier else (state.earlier + moved).sortedBy { it.at },
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
        message.photo != null && message.text.isNotBlank() -> "Фото: ${message.text}"
        message.photo != null -> "Фото"
        else -> message.text
    }

    /** Копия для пуша: не длиннее [maxBytes] байт текста, чтобы пуш уложился в 4 КБ. */
    fun preview(message: ChatMessage, maxBytes: Int = 1500): ChatPreview {
        var text = message.text
        while (text.toByteArray(Charsets.UTF_8).size > maxBytes) text = text.dropLast(maxOf(1, text.length / 10))
        if (text.length < message.text.length) text = text.trimEnd() + "…"
        return ChatPreview(seq = message.seq, at = message.at, text = text, photo = message.photo != null)
    }

    /**
     * Лента для экрана, от новых к старым (список рисуется снизу вверх): сообщения, разделители дней,
     * группы подряд от одного человека и отметки «ушло» и «прочитано».
     */
    fun rows(state: ChatState, me: Person, zone: ZoneId = ZoneId.systemDefault()): List<ChatRow> {
        data class Item(val message: ChatMessage, val mine: Boolean, val earlier: Boolean)
        // Удалённые сообщения исчезают совсем, как будто их и не было.
        val items = buildList {
            state.earlier.forEach { if (!it.deleted) add(Item(it, it.from == me.id, true)) }
            state.mine.forEach { if (!it.deleted) add(Item(it, true, false)) }
            state.theirs.forEach { if (!it.deleted) add(Item(it, false, false)) }
        }.sortedWith(compareBy<Item>({ !it.earlier }, { it.message.at }, { it.message.from }, { it.message.seq }))
        val byKey = HashMap<String, ChatMessage>(state.mine.size + state.theirs.size)
        state.mine.forEach { if (!it.deleted) byKey[it.key] = it }
        state.theirs.forEach { if (!it.deleted) byKey[it.key] = it }

        val out = ArrayList<ChatRow>(items.size + 16)
        var previousDay: LocalDate? = null
        items.forEachIndexed { index, item ->
            val day = Instant.ofEpochMilli(item.message.at).atZone(zone).toLocalDate()
            if (day != previousDay) {
                out += ChatRow.Day(day)
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
            )
        }
        return out.asReversed()
    }
}
