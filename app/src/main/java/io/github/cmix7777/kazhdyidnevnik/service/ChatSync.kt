package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import android.net.Uri
import io.github.cmix7777.kazhdyidnevnik.data.ChatCircle
import io.github.cmix7777.kazhdyidnevnik.data.ChatCloud
import io.github.cmix7777.kazhdyidnevnik.data.ChatFile
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatSticker
import io.github.cmix7777.kazhdyidnevnik.data.ChatVoice
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.ChatPhoto
import io.github.cmix7777.kazhdyidnevnik.data.ChatPreviewSeal
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.CloudCrypto
import io.github.cmix7777.kazhdyidnevnik.data.CloudException
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.ChatNotifier
import io.github.cmix7777.kazhdyidnevnik.notify.ChatSendWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Переписка на двоих без своего сервера: сообщения лежат в бесплатном Firestore (зашифрованные кодом связи).
 * Пока экран переписки открыт, второй телефон опрашивается раз в 2–4 секунды (один короткий запрос),
 * пока открыто приложение — раз в 20 секунд, в фоне — примерно раз в 15 минут. Если настроены
 * мгновенные уведомления ([ChatPush]), о новом сообщении телефон узнаёт сразу.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object ChatSync {

    data class Status(
        val loaded: Boolean = false,
        val paired: Boolean = false,
        val partnerTyping: Boolean = false,
        /** Есть ли переписка у второго человека (null — ещё не знаем). */
        val partnerHere: Boolean? = null,
        /** Чем кончилась последняя попытка связаться с облаком, если неудачей. */
        val problem: String? = null,
        /** Своя переписка забирается из облака (после переустановки). */
        val restoring: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private val typingWriter = Dispatchers.IO.limitedParallelism(1)
    private val net = Mutex()
    private val files = Mutex()
    private val lock = Any()

    private val mutableState = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = mutableState.asStateFlow()
    private val mutableStatus = MutableStateFlow(Status())
    val status: StateFlow<Status> = mutableStatus.asStateFlow()

    /** Экран переписки на виду: уведомления не нужны, всё сразу прочитано. */
    @Volatile
    var visible = false
        private set

    @Volatile private var target: File? = null
    @Volatile private var lastActivity = 0L
    @Volatile private var lastTypingSent = 0L
    @Volatile private var theirTyping = -1L
    @Volatile private var theirTypingChanged = 0L
    private val persistQueued = AtomicBoolean(false)
    private val chatWatchers = AtomicInteger(0)
    private val mainWatchers = AtomicInteger(0)
    private var loop: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val fileFailures = ConcurrentHashMap<String, Long>()
    private var tagFor: Pair<String, String>? = null

    private const val FILE = "chat.json"
    private const val TYPING_EVERY_MS = 3_000L
    private const val TYPING_SHOWN_MS = 6_500L
    private const val FAST_MS = 2_000L
    private const val CALM_MS = 4_000L
    private const val MAIN_MS = 20_000L
    private const val ACTIVE_MS = 3 * 60_000L
    private const val FILE_RETRY_MS = 60_000L

    // ---- состояние на телефоне ----

    /** Прочитать переписку с диска (один раз) и сверить код связи. Вызывать не с главного потока. */
    fun ensureLoaded(context: Context): ChatState {
        val app = context.applicationContext
        val code = AppSettings(app).pairCode
        synchronized(lock) {
            if (target == null) {
                val file = File(app.filesDir, FILE)
                val loaded = if (file.exists()) {
                    ChatLogic.decodeState(runCatching { file.readText() }.getOrDefault("")) ?: run {
                        runCatching { file.copyTo(File(app.filesDir, "chat.bad.json"), overwrite = true) }
                        ChatState()
                    }
                } else {
                    ChatState()
                }
                target = file
                mutableState.value = loaded
            }
            if (code != null) {
                val tag = tagOf(code)
                if (mutableState.value.pair != tag) {
                    write(ChatLogic.repair(mutableState.value, tag))
                    theirTyping = -1L
                    mutableStatus.update { it.copy(partnerHere = null, partnerTyping = false, problem = null) }
                }
            }
        }
        mutableStatus.update { it.copy(loaded = true, paired = code != null) }
        return mutableState.value
    }

    /** Прочитать переписку в фоне (для экранов). */
    fun loadAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { ensureLoaded(app) }
    }

    private fun tagOf(code: String): String {
        tagFor?.let { (c, tag) -> if (c == code) return tag }
        return CloudCrypto.derive(code).pairTag.also { tagFor = code to it }
    }

    private fun update(change: (ChatState) -> ChatState): ChatState = synchronized(lock) {
        val before = mutableState.value
        val after = change(before)
        if (after != before) write(after)
        after
    }

    /** Под [lock]: новое состояние видно сразу, на диск уходит чуть позже одной записью. */
    private fun write(state: ChatState) {
        mutableState.value = state
        if (target == null) return
        if (persistQueued.getAndSet(true)) return
        scope.launch(writer) {
            delay(120)
            persistQueued.set(false)
            persist(mutableState.value)
        }
    }

    private fun persist(state: ChatState) {
        val file = target ?: return
        runCatching {
            val temp = File(file.parentFile, "$FILE.tmp")
            temp.writeText(ChatLogic.encodeState(state))
            if (!temp.renameTo(file)) {
                file.writeText(ChatLogic.encodeState(state))
                temp.delete()
            }
        }
    }

    /** Дописать на диск сейчас (перед тем как фоновая задача закончится). */
    suspend fun flush() = withContext(writer) { persist(mutableState.value) }

    // ---- экраны ----

    /** Экран переписки открылся. */
    fun open(context: Context) {
        val app = context.applicationContext
        visible = true
        chatWatchers.incrementAndGet()
        lastActivity = System.currentTimeMillis()
        ChatNotifier.cancel(app)
        scope.launch {
            ensureLoaded(app)
            markRead(app)
        }
        startLoop(app)
        wake.trySend(Unit)
    }

    /** Экран переписки закрылся или ушёл в фон. */
    fun close(context: Context) {
        visible = false
        chatWatchers.updateAndGet { maxOf(0, it - 1) }
        stopTyping(context)
    }

    /** Открыто приложение: изредка проверять новые сообщения (для значка). Возвращает, чем остановить. */
    fun watchMain(context: Context): () -> Unit {
        val app = context.applicationContext
        mainWatchers.incrementAndGet()
        loadAsync(app)
        startLoop(app)
        wake.trySend(Unit)
        return { mainWatchers.updateAndGet { maxOf(0, it - 1) } }
    }

    private fun startLoop(app: Context) {
        synchronized(lock) {
            if (loop?.isActive == true) return
            loop = scope.launch {
                var failures = 0
                while (chatWatchers.get() > 0 || mainWatchers.get() > 0) {
                    val ok = if (AppSettings(app).pairCode != null) pull(app) else true
                    failures = if (ok) 0 else failures + 1
                    val now = System.currentTimeMillis()
                    val pause = when {
                        failures > 0 -> minOf(30_000L, 2_000L shl minOf(failures, 4))
                        chatWatchers.get() > 0 ->
                            if (status.value.partnerTyping || now - lastActivity < ACTIVE_MS) FAST_MS else CALM_MS
                        else -> MAIN_MS
                    }
                    withTimeoutOrNull(pause) { wake.receive() }
                }
            }
        }
    }

    // ---- действия ----

    /**
     * Отправить текст. [replyTo] — ключ сообщения, на которое ответ, [effect] — эффект на всю переписку,
     * [secret] — под невидимыми чернилами, [hand] — запиской своим почерком.
     */
    fun send(
        context: Context,
        text: String,
        replyTo: String? = null,
        effect: String? = null,
        secret: Boolean = false,
        hand: Boolean = false,
    ) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        val owner = settings.owner ?: return
        if (settings.pairCode == null || text.isBlank()) return
        scope.launch(writer) {
            ensureLoaded(app)
            update { ChatLogic.append(it, owner, text, System.currentTimeMillis(), replyTo, effect = effect, secret = secret, hand = hand) }
            lastActivity = System.currentTimeMillis()
            uploadNow(app)
        }
        stopTyping(app)
    }

    /** Отправить фото: сжимается здесь же, в облако уходит зашифрованным. */
    fun sendPhoto(context: Context, uri: Uri, caption: String = "", replyTo: String? = null) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        val owner = settings.owner ?: return
        if (settings.pairCode == null) return
        scope.launch {
            ensureLoaded(app)
            val prepared = ChatFiles.prepare(app, uri)
            if (prepared == null) {
                mutableStatus.update { it.copy(problem = "Не получилось открыть фото") }
                return@launch
            }
            ChatFiles.save(app, prepared.photo.id, prepared.bytes)
            withContext(writer) {
                update { ChatLogic.append(it, owner, caption, System.currentTimeMillis(), replyTo, prepared.photo) }
            }
            lastActivity = System.currentTimeMillis()
            uploadNow(app)
        }
    }

    /** Голосовое: файл уже записан, здесь он копируется к остальным и уходит в облако. */
    fun sendVoice(context: Context, bytes: ByteArray, durationMs: Long, wave: String, replyTo: String? = null) =
        sendFile(context, bytes, replyTo) { state, id, parts -> ChatLogic.append(state, owner(context), "", System.currentTimeMillis(), replyTo, voice = ChatVoice(id, durationMs, parts, wave)) }

    /** Кружочек. */
    fun sendCircle(context: Context, bytes: ByteArray, durationMs: Long, replyTo: String? = null) =
        sendFile(context, bytes, replyTo) { state, id, parts -> ChatLogic.append(state, owner(context), "", System.currentTimeMillis(), replyTo, circle = ChatCircle(id, durationMs, parts)) }

    /** Стикер: у каждого отправленного свой номер, второй телефон стирает его в облаке, когда скачает. */
    fun sendSticker(context: Context, bytes: ByteArray) =
        sendFile(context, bytes, null) { state, id, _ -> ChatLogic.append(state, owner(context), "", System.currentTimeMillis(), sticker = ChatSticker(id)) }

    /**
     * Отправить любой файл. Он уходит в облако частями и стирается там, когда второй телефон его скачает.
     * Возвращает, что не так (слишком большой, не открылся), или null — файл в очереди на отправку.
     */
    suspend fun sendDocument(context: Context, uri: Uri, replyTo: String? = null): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val resolver = app.contentResolver
        var name = "файл"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        if (size > MAX_FILE_BYTES) return@withContext "Файл больше ${MAX_FILE_BYTES / 1_000_000} МБ: такой в переписку не пролезет"
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext "Файл не открылся"
        if (bytes.size > MAX_FILE_BYTES) return@withContext "Файл больше ${MAX_FILE_BYTES / 1_000_000} МБ: такой в переписку не пролезет"
        if (bytes.isEmpty()) return@withContext "Файл пустой"
        val mime = resolver.getType(uri).orEmpty()
        sendFile(app, bytes, replyTo) { state, id, parts ->
            ChatLogic.append(state, owner(app), "", System.currentTimeMillis(), replyTo, file = ChatFile(id, name.take(120), bytes.size.toLong(), mime, parts))
        }
        null
    }

    /** Файлы в переписке не больше этого: в облаке они идут частями по 215 КБ. */
    const val MAX_FILE_BYTES = 25_000_000

    private fun owner(context: Context): Person = AppSettings(context.applicationContext).owner ?: Person.AIZAT

    private fun sendFile(context: Context, bytes: ByteArray, @Suppress("UNUSED_PARAMETER") replyTo: String?, add: (ChatState, String, Int) -> ChatState) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        if (settings.owner == null || settings.pairCode == null || bytes.isEmpty()) return
        scope.launch {
            ensureLoaded(app)
            val id = ChatFiles.newId()
            ChatFiles.save(app, id, bytes)
            val parts = ChatLogic.partsFor(bytes.size)
            withContext(writer) { update { add(it, id, parts) } }
            lastActivity = System.currentTimeMillis()
            uploadNow(app)
        }
    }

    /** Реакция на сообщение (тот же смайлик второй раз снимает её). */
    fun react(context: Context, key: String, emoji: String?) {
        val app = context.applicationContext
        scope.launch(writer) {
            update { ChatLogic.react(it, key, emoji) }
            uploadNow(app)
        }
    }

    /** Секрет проявлен (только на этом телефоне). */
    fun openSecret(key: String) {
        scope.launch(writer) { update { ChatLogic.open(it, key) } }
    }

    /** Эффект сообщения сыграл, больше не повторять. */
    fun markPlayed(key: String) {
        scope.launch(writer) { update { ChatLogic.played(it, key) } }
    }

    /** Сохранить свой почерк: файл уходит в облако, второй телефон подхватит его по голове. */
    fun saveFont(context: Context, bytes: ByteArray) {
        val app = context.applicationContext
        scope.launch {
            ensureLoaded(app)
            val id = ChatFiles.newId()
            ChatFiles.save(app, id, bytes)
            withContext(writer) { update { ChatLogic.newFont(it, id) } }
            uploadNow(app)
        }
    }

    /** Удалить своё сообщение у обоих. */
    fun delete(context: Context, message: ChatMessage) {
        val app = context.applicationContext
        scope.launch(writer) {
            update { ChatLogic.deleteMine(it, message.seq) }
            uploadNow(app)
        }
    }

    /** Повторить отправку того, что не ушло. */
    fun retry(context: Context) {
        val app = context.applicationContext
        scope.launch { uploadNow(app) }
    }

    private fun uploadNow(app: Context) {
        scope.launch { if (!upload(app)) ChatSendWorker.enqueue(app) }
    }

    /**
     * Переписка правда перед глазами: экран открыт, телефон не погас и не заблокирован.
     * Только тогда пришедшее считается прочитанным.
     */
    private fun seen(app: Context): Boolean {
        if (!visible) return false
        val awake = runCatching { app.getSystemService(android.os.PowerManager::class.java)?.isInteractive }.getOrNull() ?: true
        val locked = runCatching { app.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked }.getOrNull() ?: false
        return awake && !locked
    }

    /** Отметить всё полученное прочитанным (экран переписки открыт и на виду). */
    fun markRead(context: Context) {
        val app = context.applicationContext
        if (!seen(app)) return
        val last = ChatLogic.lastSeq(state.value.theirs)
        if (last > state.value.readUpTo) {
            update { it.copy(readUpTo = maxOf(it.readUpTo, last), notifiedUpTo = maxOf(it.notifiedUpTo, last)) }
            scope.launch { publishRead(app) }
        }
        ChatNotifier.cancel(app)
    }

    /** Человек набирает текст: второй увидит «печатает…». Отправляется не чаще раза в 3 секунды. */
    fun typing(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < TYPING_EVERY_MS) return
        lastTypingSent = now
        lastActivity = now
        putTyping(context.applicationContext, now)
    }

    /** Перестал набирать (стёр текст, отправил, закрыл экран). */
    fun stopTyping(context: Context) {
        if (lastTypingSent == 0L) return
        lastTypingSent = 0L
        putTyping(context.applicationContext, 0L)
    }

    private fun putTyping(app: Context, value: Long) {
        scope.launch(typingWriter) {
            val settings = AppSettings(app)
            val owner = settings.owner ?: return@launch
            val code = settings.pairCode ?: return@launch
            runCatching { cloud(settings).putTyping(code, owner, value) }
        }
    }

    // ---- обмен с облаком ----

    private fun cloud(settings: AppSettings) = ChatCloud(JsoupTransport, settings.cloudTokens)

    /** Забрать новое от второго человека и отправить своё, если что-то не ушло. true — облако ответило. */
    suspend fun pull(context: Context): Boolean {
        val app = context.applicationContext
        ensureLoaded(app)
        val settings = AppSettings(app)
        val owner = settings.owner ?: return false
        val code = settings.pairCode ?: return false
        val ok = net.withLock {
            guarded {
                val cloud = cloud(settings)
                restoreIfNeeded(cloud, code, owner)
                pullLocked(app, settings, cloud, code, owner)
                if (ChatLogic.hasPending(state.value, settings.pushToken, settings.pushHook)) {
                    uploadLocked(app, settings, cloud, code, owner)
                }
                publishReadLocked(app, settings, cloud, code, owner)
            }
        }
        if (ok) downloadMissing(app)
        return ok
    }

    /** Отправить своё. true — всё ушло. */
    suspend fun upload(context: Context): Boolean {
        val app = context.applicationContext
        ensureLoaded(app)
        val settings = AppSettings(app)
        val owner = settings.owner ?: return false
        val code = settings.pairCode ?: return false
        return net.withLock {
            guarded { uploadLocked(app, settings, cloud(settings), code, owner) }
        }
    }

    private suspend fun publishRead(app: Context) {
        val settings = AppSettings(app)
        val owner = settings.owner ?: return
        val code = settings.pairCode ?: return
        net.withLock { guarded { publishReadLocked(app, settings, cloud(settings), code, owner) } }
    }

    private inline fun guarded(block: () -> Unit): Boolean = try {
        block()
        mutableStatus.update { it.copy(problem = null) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: CloudException) {
        mutableStatus.update { it.copy(problem = e.message ?: "Облако не ответило") }
        false
    } catch (e: IOException) {
        mutableStatus.update { it.copy(problem = "Нет связи") }
        false
    } catch (e: Exception) {
        mutableStatus.update { it.copy(problem = e.message ?: "Что-то пошло не так") }
        false
    }

    /** Своя переписка ещё не сверена с облаком (новая установка или новый код): забрать её, прежде чем писать. */
    private fun restoreIfNeeded(cloud: ChatCloud, code: String, owner: Person) {
        if (state.value.restored) return
        mutableStatus.update { it.copy(restoring = true) }
        try {
            val remote = try {
                cloud.head(code, owner)
            } catch (e: CloudException) {
                // Голова есть, но не расшифровывается этим кодом: её можно спокойно переписать.
                if (e.code in 200..299) null else throw e
            }
            if (remote == null) {
                // Своей головы ещё нет: создадим пустую, чтобы отметки «прочитано» и «печатает» было куда писать.
                update { it.copy(restored = true, revision = it.revision + 1) }
            } else {
                val (head, mark) = remote
                val pages = (0 until head.pages).map { index ->
                    cloud.page(code, owner, index) ?: throw CloudException("Старые сообщения пока не прочитались")
                }
                update { ChatLogic.restoreMine(it, head, pages, mark) }
            }
        } finally {
            mutableStatus.update { it.copy(restoring = false) }
        }
    }

    private fun pullLocked(app: Context, settings: AppSettings, cloud: ChatCloud, code: String, owner: Person) {
        val partner = owner.partner
        val mark = cloud.mark(code, partner)
        if (mark == null) {
            mutableStatus.update { it.copy(partnerHere = false, partnerTyping = false) }
            return
        }
        mutableStatus.update { it.copy(partnerHere = true) }
        if (mark.version != state.value.theirVersion) {
            val (head, headMark) = cloud.head(code, partner) ?: return
            val before = ChatLogic.lastSeq(state.value.theirs)
            val merge = ChatLogic.mergeTheirs(state.value, head, headMark.version)
            var merged = merge.state
            val missing = merge.missingFrom
            if (missing != null) {
                var index = head.pages - 1
                while (index >= 0) {
                    val page = cloud.page(code, partner, index) ?: break
                    merged = ChatLogic.mergeTheirsPage(merged, page)
                    if ((page.messages.firstOrNull()?.seq ?: 0L) <= missing) break
                    index--
                }
            }
            update {
                val next = it.copy(
                    theirs = merged.theirs,
                    earlier = merged.earlier,
                    theirVersion = merged.theirVersion,
                    theirPush = merged.theirPush,
                    theirHook = merged.theirHook,
                    theirReactions = merged.theirReactions,
                    theirFont = merged.theirFont,
                )
                if (!merge.restarted) next else next.copy(
                    readUpTo = minOf(it.readUpTo, merged.readUpTo),
                    notifiedUpTo = minOf(it.notifiedUpTo, merged.notifiedUpTo),
                    readSent = -1,
                )
            }
            if (ChatLogic.lastSeq(merged.theirs) > before) {
                lastActivity = System.currentTimeMillis()
                theirTyping = 0L
                mutableStatus.update { it.copy(partnerTyping = false) }
            }
            // Второй телефон уже настроил мгновенные уведомления: подхватываем его ссылку.
            val hook = merged.theirHook
            if (settings.pushHook == null && hook != null) scope.launch { ChatPush.adopt(app, hook) }
        }
        // Отметка второго телефона как есть: после сброса нумерации она может стать меньше, и это правда.
        if (mark.read != state.value.theirRead) update { it.copy(theirRead = mark.read) }
        observeTyping(mark.typing)
        afterArrival(app, owner)
    }

    private fun observeTyping(value: Long) {
        val now = System.currentTimeMillis()
        when {
            value == 0L -> theirTyping = 0L
            // Первый взгляд: неизвестно, давно ли это число. Покажем, только когда оно сменится.
            theirTyping == -1L -> theirTyping = value
            value != theirTyping -> {
                theirTyping = value
                theirTypingChanged = now
            }
        }
        val shown = value != 0L && theirTypingChanged > 0 && now - theirTypingChanged < TYPING_SHOWN_MS
        if (shown != status.value.partnerTyping) mutableStatus.update { it.copy(partnerTyping = shown) }
    }

    private fun afterArrival(app: Context, owner: Person) {
        val current = state.value
        if (seen(app)) {
            val last = ChatLogic.lastSeq(current.theirs)
            if (last > current.readUpTo) {
                update { it.copy(readUpTo = maxOf(it.readUpTo, last), notifiedUpTo = maxOf(it.notifiedUpTo, last)) }
            }
            return
        }
        val fresh = ChatLogic.toNotify(current)
        if (fresh.isEmpty()) return
        ChatNotifier.show(app, owner, ChatLogic.unreadMessages(current))
        update { it.copy(notifiedUpTo = maxOf(it.notifiedUpTo, fresh.maxOf { m -> m.seq })) }
    }

    private fun uploadLocked(app: Context, settings: AppSettings, cloud: ChatCloud, code: String, owner: Person) {
        restoreIfNeeded(cloud, code, owner)
        for (id in state.value.pendingFiles.toList()) {
            val bytes = ChatFiles.read(app, id)
            if (bytes != null) {
                // Большие файлы (голосовые, кружочки) уходят частями: каждая проходит правила базы.
                val parts = ChatLogic.partsFor(bytes.size)
                ChatLogic.partIds(id, parts).forEachIndexed { index, partId ->
                    val from = index * ChatLogic.FILE_PART_BYTES
                    val to = minOf(bytes.size, from + ChatLogic.FILE_PART_BYTES)
                    cloud.putFile(code, partId, bytes.copyOfRange(from, to), System.currentTimeMillis())
                }
            }
            update { it.copy(pendingFiles = it.pendingFiles - id) }
        }
        for (id in state.value.wipeFiles.toList()) {
            cloud.wipeFile(code, id, System.currentTimeMillis())
            update { it.copy(wipeFiles = it.wipeFiles - id) }
        }
        val snapshot = state.value
        val push = settings.pushToken
        val hook = settings.pushHook
        if (!ChatLogic.needsHead(snapshot, push, hook)) return
        val version = maxOf(System.currentTimeMillis(), snapshot.myVersion + 1)
        val plan = ChatLogic.planUpload(snapshot, owner, push, hook)
        cloud.upload(code, owner, plan.pages, plan.head, version)
        update { ChatLogic.afterUpload(it, plan, version, snapshot.revision) }
        val fresh = snapshot.mine.lastOrNull { it.seq > snapshot.uploaded && !it.deleted }
        // Пуш уходит отдельно, чтобы медленный ответ пересылки не задерживал опрос.
        if (fresh != null) scope.launch { runCatching { pushPartner(settings, code, owner, fresh) } }
    }

    private fun publishReadLocked(app: Context, settings: AppSettings, cloud: ChatCloud, code: String, owner: Person) {
        val upTo = state.value.readUpTo
        if (upTo <= state.value.readSent) return
        var ok = cloud.putRead(code, owner, upTo)
        if (!ok && state.value.myVersion == 0L) {
            // Своей головы ещё нет: создаём её и пробуем снова.
            update { it.copy(revision = it.revision + 1) }
            uploadLocked(app, settings, cloud, code, owner)
            ok = cloud.putRead(code, owner, upTo)
        }
        if (!ok) throw CloudException("Не удалось отметить сообщения прочитанными")
        update { it.copy(readSent = maxOf(it.readSent, upTo)) }
    }

    /** Мгновенный пуш второму телефону о новом сообщении (если пересылка настроена). */
    private fun pushPartner(settings: AppSettings, code: String, owner: Person, message: ChatMessage) {
        val current = state.value
        val token = current.theirPush ?: return
        val hook = settings.pushHook ?: current.theirHook ?: return
        val sealed = ChatPreviewSeal.seal(code, owner, ChatLogic.preview(message))
        val data = mapOf("k" to "m", "f" to owner.id, "s" to message.seq.toString(), "p" to sealed)
        if (ChatPush.send(hook, token, data) == ChatPush.Sent.UNREGISTERED) {
            update { it.copy(theirPush = null) }
        }
    }

    // ---- фото ----

    /**
     * Скачать файл (фото, голосовое, кружочек, стикер, почерк), если его ещё нет на телефоне.
     * Облако для вложений — почтовый ящик: файлы второго человека после скачивания стираются там
     * ([wipe]), а на телефонах остаются. Сохранить их насовсем можно архивом переписки.
     */
    fun ensureFile(context: Context, id: String, parts: Int = 1, wipe: Boolean = false) {
        val app = context.applicationContext
        if (id.isEmpty() || ChatFiles.exists(app, id) || ChatFiles.gone(app, id)) return
        val failedAt = fileFailures[id]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FILE_RETRY_MS) return
        scope.launch {
            files.withLock {
                if (ChatFiles.exists(app, id)) return@withLock
                val settings = AppSettings(app)
                val code = settings.pairCode ?: return@withLock
                try {
                    val cloud = cloud(settings)
                    val ids = ChatLogic.partIds(id, parts)
                    // Вложение второго человека стирается в облаке после скачивания, кто бы ни попросил файл.
                    val wipeAfter = wipe || state.value.theirs.any { m -> m.files.any { it.first == id } }
                    val chunks = ids.map { cloud.file(code, it) }
                    if (chunks.any { it == null }) {
                        // Файл уже стёрт после доставки (или не дошёл): дальше показывается заглушка.
                        if (chunks.first() == null) ChatFiles.markGone(app, id) else fileFailures[id] = System.currentTimeMillis()
                    } else {
                        val out = java.io.ByteArrayOutputStream(chunks.sumOf { it!!.size })
                        chunks.forEach { out.write(it!!) }
                        ChatFiles.save(app, id, out.toByteArray())
                        if (wipeAfter) ids.forEach { runCatching { cloud.wipeFile(code, it, System.currentTimeMillis()) } }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fileFailures[id] = System.currentTimeMillis()
                }
            }
        }
    }

    /** Файлы сообщения: свои не стираются в облаке (их стирает второй телефон), чужие — после скачивания. */
    fun ensureFiles(context: Context, message: ChatMessage, mine: Boolean) {
        message.files.forEach { (id, parts) -> ensureFile(context, id, parts, wipe = !mine) }
    }

    private fun downloadMissing(app: Context) {
        val current = state.value
        current.theirFont?.let { ensureFile(app, it) }
        current.theirs.asReversed().asSequence()
            .filter { !it.deleted && it.hasMedia }
            .flatMap { it.files.asSequence() }
            .filter { (id, _) -> !ChatFiles.exists(app, id) && !ChatFiles.gone(app, id) }
            .take(12)
            .forEach { (id, parts) -> ensureFile(app, id, parts, wipe = true) }
    }

    // ---- фоновые задачи и уведомления ----

    /** Пришёл пуш: показать уведомление сразу (по копии из пуша) и забрать сообщение целиком. */
    fun onPush(context: Context, data: Map<String, String>) {
        val app = context.applicationContext
        ensureLoaded(app)
        val settings = AppSettings(app)
        val owner = settings.owner ?: return
        val code = settings.pairCode ?: return
        if (data["k"] != "m" || data["f"] != owner.partner.id) return
        val preview = data["p"]?.let { ChatPreviewSeal.open(code, owner.partner, it) }
        if (preview != null && !visible) {
            val current = state.value
            if (preview.seq > current.readUpTo && preview.seq > current.notifiedUpTo && current.theirs.none { it.seq == preview.seq }) {
                val message = ChatLogic.fromPreview(owner.partner.id, preview)
                ChatNotifier.show(app, owner, ChatLogic.unreadMessages(current) + message)
                update { it.copy(notifiedUpTo = maxOf(it.notifiedUpTo, preview.seq)) }
            }
        }
        wake.trySend(Unit)
        runBlocking {
            withTimeoutOrNull(8_000) { pull(app) }
            flush()
        }
    }

    /** Ответ прямо из уведомления. */
    suspend fun replyFromNotification(context: Context, text: String) {
        val app = context.applicationContext
        ensureLoaded(app)
        val owner = AppSettings(app).owner ?: return
        withContext(writer) {
            update { current ->
                val last = ChatLogic.lastSeq(current.theirs)
                val read = current.copy(readUpTo = maxOf(current.readUpTo, last), notifiedUpTo = maxOf(current.notifiedUpTo, last))
                ChatLogic.append(read, owner, text, System.currentTimeMillis())
            }
        }
        ChatNotifier.cancel(app)
        val sent = withTimeoutOrNull(9_000) { upload(app).also { if (it) publishRead(app) } } ?: false
        if (!sent) ChatSendWorker.enqueue(app)
        flush()
    }

    /** «Прочитано» из уведомления. */
    suspend fun readFromNotification(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        val last = ChatLogic.lastSeq(state.value.theirs)
        update { it.copy(readUpTo = maxOf(it.readUpTo, last), notifiedUpTo = maxOf(it.notifiedUpTo, last)) }
        ChatNotifier.cancel(app)
        withTimeoutOrNull(8_000) { publishRead(app) }
        flush()
    }

    /** Фоновая проверка (примерно раз в 15 минут). */
    suspend fun backgroundCheck(context: Context): Boolean {
        val app = context.applicationContext
        if (AppSettings(app).pairCode == null) return true
        val ok = pull(app)
        flush()
        return ok
    }

    /** Свой токен пушей сменился или ссылка настроена: положить в голову, чтобы второй телефон знал. */
    fun pushChanged(context: Context) {
        val app = context.applicationContext
        scope.launch { if (AppSettings(app).pairCode != null && !upload(app)) ChatSendWorker.enqueue(app) }
    }
}
