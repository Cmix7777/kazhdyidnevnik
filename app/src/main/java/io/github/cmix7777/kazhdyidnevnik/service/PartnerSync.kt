package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import io.github.cmix7777.kazhdyidnevnik.data.CloudException
import io.github.cmix7777.kazhdyidnevnik.data.CloudShares
import io.github.cmix7777.kazhdyidnevnik.data.DiaryRepository
import io.github.cmix7777.kazhdyidnevnik.data.GreetingDrops
import io.github.cmix7777.kazhdyidnevnik.data.PairCode
import io.github.cmix7777.kazhdyidnevnik.data.PartnerEventsRepository
import io.github.cmix7777.kazhdyidnevnik.data.PartnerShare
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.SharedEvents
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.PartnerHeard
import io.github.cmix7777.kazhdyidnevnik.notify.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Связь двух телефонов через бесплатное облако: свои дела уходят туда сами (в зашифрованном виде),
 * дела второго человека приходят сюда. Нужен общий код связи (создаёт один телефон, второй вставляет).
 * Работает, пока телефоны связаны; ручная передача сообщением тоже остаётся.
 */
object PartnerSync {

    /** Что показывать в настройках и на странице второго человека. */
    data class Info(
        val paired: Boolean = false,
        /** Телефон был связан по старому коду (до шифрования), связь нужно создать заново. */
        val legacy: Boolean = false,
        val running: Boolean = false,
        /** Когда последний обмен прошёл успешно (0 — ещё не было). */
        val lastSyncMillis: Long = 0L,
        /** Чем кончилась последняя попытка, если неудачей. */
        val problem: String? = null,
        /** Что слышно от второго человека: см. [AppSettings.partnerHeardState]. */
        val partnerState: String = "",
        /** Когда второй человек последний раз отправлял дела (0 — неизвестно). */
        val partnerMillis: Long = 0L,
    )

    enum class JoinResult { Done, NoCode, OldCode, AlreadyJoined }

    /**
     * Строка для настроек: что слышно от второго человека. null — ещё не было обмена.
     * [stamp] превращает время в «13 окт, 21:40».
     */
    fun partnerNote(info: Info, partner: Person, stamp: (Long) -> String): String? = when (info.partnerState) {
        PartnerHeard.OK ->
            if (info.partnerMillis > 0) "Последние дела от ${partner.genitive}: ${stamp(info.partnerMillis)}." else null
        PartnerHeard.NONE ->
            "От ${partner.genitive} по этому коду пока ничего не приходило. Скорее всего, у ${partner.genitive} " +
                "другой код или связь не создана заново. Нажми «Отправить код ещё раз», а ${partner.shortName} " +
                "пусть вставит его у себя в настройках."
        PartnerHeard.UNREADABLE ->
            "Дела от ${partner.genitive} пришли, но не читаются. Скорее всего, у ${partner.genitive} старая " +
                "версия приложения: пусть обновится."
        else -> null
    }

    private val mutableInfo = MutableStateFlow(Info())
    val info: StateFlow<Info> = mutableInfo.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private const val MIN_GAP_MS = 60_000L
    private const val DEBOUNCE_MS = 2_500L
    private const val REPUBLISH_MS = 24 * 60 * 60 * 1000L

    /** Прочитать сохранённое состояние связи (после запуска или смены настроек). */
    fun load(context: Context) {
        val settings = AppSettings(context)
        mutableInfo.update {
            it.copy(
                paired = settings.pairCode != null,
                legacy = settings.hasLegacyPairing,
                lastSyncMillis = settings.lastSyncMillis,
                problem = settings.lastSyncResult.ifEmpty { null },
                partnerState = settings.partnerHeardState,
                partnerMillis = settings.partnerHeardMillis,
            )
        }
    }

    /** Сообщение с кодом связи для второго телефона; код создаётся, если его ещё нет. null — не выбрано, чей телефон. */
    fun codeMessage(context: Context): String? {
        val settings = AppSettings(context)
        val owner = settings.owner ?: return null
        val code = settings.pairCode ?: PairCode.generate().also { settings.pairCode = it }
        load(context)
        syncSoon(context)
        return PairCode.message(owner, code)
    }

    /** Подключиться по коду из скопированного сообщения. */
    fun join(context: Context, text: String): JoinResult {
        val code = PairCode.parse(text)
            ?: return if (PairCode.isLegacy(text)) JoinResult.OldCode else JoinResult.NoCode
        val settings = AppSettings(context)
        if (settings.pairCode == code) return JoinResult.AlreadyJoined
        settings.pairCode = code
        // Новый код: что было слышно по старому, больше не важно.
        settings.partnerHeardState = ""
        settings.cloudTokens.save(null)
        load(context)
        syncNow(context)
        return JoinResult.Done
    }

    /** Отвязать: дела больше не уходят и не приходят, уже полученные остаются. */
    fun unpair(context: Context) {
        val settings = AppSettings(context)
        settings.pairCode = null
        settings.recordSync(0L, "")
        settings.partnerHeardState = ""
        load(context)
    }

    /** Обменяться сразу (в фоне приложения). */
    fun syncNow(context: Context, force: Boolean = true) {
        val app = context.applicationContext
        scope.launch { sync(app, force) }
    }

    /** Обменяться через пару секунд: так серия правок уходит одной отправкой. */
    fun syncSoon(context: Context) {
        val app = context.applicationContext
        if (AppSettings(app).pairCode == null) return
        synchronized(this) {
            pending?.cancel()
            pending = scope.launch {
                delay(DEBOUNCE_MS)
                sync(app, force = true)
            }
        }
    }

    /**
     * Отправить свои дела (если они изменились) и забрать дела второго человека.
     * При [force] = false после удачного обмена повторяется не чаще раза в минуту.
     * Возвращает true, если обмен удался.
     */
    suspend fun sync(context: Context, force: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        val owner = settings.owner
        val code = settings.pairCode
        if (owner == null || code == null) {
            load(app)
            return@withContext false
        }
        mutex.withLock {
            val before = System.currentTimeMillis()
            if (!force && settings.lastSyncResult.isEmpty() && before - settings.lastSyncMillis < MIN_GAP_MS) {
                return@withLock true
            }
            mutableInfo.update { it.copy(paired = true, running = true) }
            val problem = try {
                exchange(app, settings, owner, code)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: CloudException) {
                e.message ?: "Облако не ответило"
            } catch (e: IOException) {
                "Нет связи с облаком"
            } catch (e: Exception) {
                e.message ?: "Что-то пошло не так"
            }
            val finished = System.currentTimeMillis()
            if (problem == null) {
                settings.recordSync(finished, "")
            } else {
                settings.recordSync(settings.lastSyncMillis, problem)
            }
            mutableInfo.update {
                it.copy(
                    running = false,
                    lastSyncMillis = settings.lastSyncMillis,
                    problem = problem,
                    partnerState = settings.partnerHeardState,
                    partnerMillis = settings.partnerHeardMillis,
                )
            }
            problem == null
        }
    }

    private fun exchange(app: Context, settings: AppSettings, owner: Person, code: String) {
        val cloud = CloudShares(JsoupTransport, settings.cloudTokens)
        val now = System.currentTimeMillis()

        val diary = DiaryRepository.current(app.filesDir)
        val mine = PartnerShare.relevant(diary.events, LocalDate.now())
        val dates = PartnerShare.relevantDates(diary.deadlines)
        val drops = GreetingDrops.recent(settings.sentDrops, now)
        val fingerprint = PairCode.fingerprint(PartnerShare.encode(SharedEvents(owner, 0L, mine, dates, drops)))
        if (fingerprint != settings.publishedFingerprint || now - settings.publishedMillis > REPUBLISH_MS) {
            cloud.publish(code, owner, PartnerShare.encode(SharedEvents(owner, now, mine, dates, drops)), now)
            settings.publishedFingerprint = fingerprint
            settings.publishedMillis = now
        }

        // Обмен с облаком мог пройти успешно, а от второго человека по этому коду не прийти ничего
        // (например, у него другой код). Запоминаем это, чтобы показать в настройках, а не молчать.
        val remote = cloud.fetch(code, owner.partner)
        if (remote == null) {
            settings.partnerHeardState = PartnerHeard.NONE
            return
        }
        val shared = PartnerShare.decode(remote.payload)
        if (shared == null || shared.from != owner.partner) {
            settings.partnerHeardState = PartnerHeard.UNREADABLE
            return
        }
        settings.partnerHeardState = PartnerHeard.OK
        settings.partnerHeardMillis = maxOf(remote.updatedAt, shared.at)
        val current = PartnerEventsRepository.current(app.filesDir)
        if (current == null || current.from != shared.from || shared.at > current.at) {
            PartnerEventsRepository.save(app.filesDir, shared)
            ReminderScheduler.scheduleNext(app)
        }
    }
}
