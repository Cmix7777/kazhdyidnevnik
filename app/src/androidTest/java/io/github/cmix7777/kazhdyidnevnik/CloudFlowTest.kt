package io.github.cmix7777.kazhdyidnevnik

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cmix7777.kazhdyidnevnik.data.ChatCloud
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.ChatTick
import io.github.cmix7777.kazhdyidnevnik.data.CloudShares
import io.github.cmix7777.kazhdyidnevnik.data.GreetingDrops
import io.github.cmix7777.kazhdyidnevnik.data.PartnerEventsRepository
import io.github.cmix7777.kazhdyidnevnik.data.PartnerShare
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.SharedEvents
import io.github.cmix7777.kazhdyidnevnik.data.SharedList
import io.github.cmix7777.kazhdyidnevnik.data.SharedListStore
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.CloudNet
import io.github.cmix7777.kazhdyidnevnik.service.JsoupTransport
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import io.github.cmix7777.kazhdyidnevnik.testing.FakeFirestore
import io.github.cmix7777.kazhdyidnevnik.testing.MemoryTokens
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Переписка и капли целиком, как на двух телефонах: этот телефон — Айзат с настоящими ChatSync и PartnerSync,
 * второй (Настя) — тот же код облака, вызываемый прямо из теста. Облако подставное, но с правилами настоящей базы.
 */
@RunWith(AndroidJUnit4::class)
class CloudFlowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private val code = "abcdefghjkmnpqrstuvw"
    private lateinit var store: FakeFirestore
    private lateinit var nastya: ChatCloud
    private lateinit var nastyaShares: CloudShares
    private var partner = ChatState(restored = true)

    @Before
    fun setUp() {
        store = FakeFirestore()
        CloudNet.transport = store
        ChatSync.screenSeen = { true }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        val settings = AppSettings(context)
        settings.owner = Person.AIZAT
        settings.pairCode = code
        ChatSync.resetForTest(context)
        nastya = ChatCloud(store, MemoryTokens())
        nastyaShares = CloudShares(store, MemoryTokens())
        partner = ChatState(restored = true)
    }

    @After
    fun tearDown() {
        ChatSync.close(context)
        ChatSync.resetForTest(context)
        CloudNet.transport = JsoupTransport
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // ---- второй телефон ----

    private fun partnerSends(text: String) {
        val now = System.currentTimeMillis()
        partner = ChatLogic.append(partner, Person.NASTYA, text, now)
        val plan = ChatLogic.planUpload(partner, Person.NASTYA, push = null, hook = null)
        val version = maxOf(now, partner.myVersion + 1)
        nastya.upload(code, Person.NASTYA, plan.pages, plan.head, version)
        partner = ChatLogic.afterUpload(partner, plan, version, partner.revision)
        Thread.sleep(5)
    }

    private fun aizatHead() = nastya.head(code, Person.AIZAT)?.first

    // ---- этот телефон ----

    private fun pull() {
        val ok = runBlocking { ChatSync.pull(context) }
        assertTrue("обмен не удался: ${ChatSync.status.value.problem}", ok)
    }

    private fun eventually(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(100)
        }
        throw AssertionError("Не дождался: $what. Проблема: ${ChatSync.status.value.problem}")
    }

    @Test
    fun partnerMessagesArriveAndAreReadOnlyWhenChatIsOpen() {
        partnerSends("привет")
        partnerSends("как ты?")
        pull()
        val state = ChatSync.state.value
        assertEquals(listOf("привет", "как ты?"), state.theirs.map { it.text })
        assertEquals(2, ChatLogic.unread(state))
        // Пока переписка закрыта, «прочитано» второму телефону не уходит.
        assertEquals(0L, nastya.mark(code, Person.AIZAT)!!.read)
        ChatSync.open(context)
        eventually("«прочитано» дошло до второго телефона") { nastya.mark(code, Person.AIZAT)?.read == 2L }
        assertEquals(0, ChatLogic.unread(ChatSync.state.value))
        ChatSync.close(context)
        // Новое сообщение при закрытой переписке остаётся непрочитанным.
        partnerSends("ты тут?")
        pull()
        assertEquals(1, ChatLogic.unread(ChatSync.state.value))
        assertEquals(2L, nastya.mark(code, Person.AIZAT)!!.read)
    }

    @Test
    fun myMessageGetsReadMarkOnlyAfterPartnerReadsIt() {
        partnerSends("привет")
        pull()
        ChatSync.send(context, "привет, Настя")
        eventually("сообщение дошло до второго телефона") { aizatHead()?.messages?.any { it.text == "привет, Настя" } == true }
        pull()
        val sent = ChatSync.state.value.mine.single()
        assertEquals(ChatTick.SENT, ChatLogic.tick(ChatSync.state.value, sent, true))
        assertTrue(nastya.putRead(code, Person.NASTYA, sent.seq))
        pull()
        assertEquals(ChatTick.READ, ChatLogic.tick(ChatSync.state.value, ChatSync.state.value.mine.single(), true))
    }

    @Test
    fun partnerStartingOverDoesNotMakeNewMessagesLookRead() {
        repeat(3) { partnerSends("старое $it") }
        pull()
        ChatSync.open(context)
        eventually("прочитаны три старых") { nastya.mark(code, Person.AIZAT)?.read == 3L }
        ChatSync.close(context)

        // Настя переустановила приложение и начала нумерацию заново.
        partner = ChatState(restored = true)
        partnerSends("я переустановила")
        pull()
        val state = ChatSync.state.value
        assertEquals(listOf("я переустановила"), state.theirs.map { it.text })
        assertEquals(1, ChatLogic.unread(state))
        assertEquals(3, state.earlier.size)
        // Её телефон видит: новое сообщение у Айзата ещё не прочитано.
        eventually("отметка «прочитано» опустилась") { nastya.mark(code, Person.AIZAT)?.read == 0L }

        ChatSync.open(context)
        eventually("новое прочитано, когда открыл переписку") { nastya.mark(code, Person.AIZAT)?.read == 1L }
    }

    @Test
    fun reinstallContinuesNumberingInsteadOfStartingOver() {
        partnerSends("привет")
        pull()
        ChatSync.send(context, "раз")
        eventually("первое ушло") { aizatHead()?.last == 1L }
        ChatSync.send(context, "два")
        eventually("второе ушло") { aizatHead()?.last == 2L }

        // Телефон потерял переписку (переустановка), код связи тот же.
        ChatSync.resetForTest(context)
        pull()
        assertEquals(listOf("раз", "два"), ChatSync.state.value.mine.map { it.text })
        ChatSync.send(context, "три")
        eventually("третье ушло с номером 3") { aizatHead()?.last == 3L }
        assertEquals(listOf(1L, 2L, 3L), aizatHead()!!.messages.map { it.seq })
    }

    @Test
    fun deletedMessageDisappearsOnTheOtherPhone() {
        partnerSends("привет")
        pull()
        ChatSync.send(context, "ой, не туда")
        eventually("сообщение ушло") { aizatHead()?.last == 1L }
        ChatSync.delete(context, ChatSync.state.value.mine.single())
        eventually("удаление дошло") { aizatHead()?.messages?.single()?.deleted == true }
    }

    @Test
    fun editedMessageChangesOnTheOtherPhone() {
        partnerSends("привет")
        pull()
        ChatSync.send(context, "првиет, Настя")
        eventually("сообщение ушло") { aizatHead()?.last == 1L }
        ChatSync.edit(context, ChatSync.state.value.mine.single(), "привет, Настя")
        eventually("правка дошла") { aizatHead()?.messages?.single()?.text == "привет, Настя" }
        assertTrue(aizatHead()!!.messages.single().edited != null)
    }

    @Test
    fun scheduledMessageGoesWhenItsTimeComes() {
        partnerSends("привет")
        pull()
        ChatSync.schedule(context, "с добрым утром", System.currentTimeMillis() + 60 * 60_000L)
        eventually("сообщение отложено") { ChatSync.state.value.scheduled.size == 1 }
        runBlocking { ChatSync.releaseDue(context) }
        // Время ещё не пришло: в облаке ничего.
        assertTrue(aizatHead()?.messages.orEmpty().none { it.text == "с добрым утром" })
        // Время пришло (как будто телефон проснулся по будильнику).
        ChatSync.cancelScheduled(context, ChatSync.state.value.scheduled.single().id)
        eventually("отменено") { ChatSync.state.value.scheduled.isEmpty() }
        ChatSync.schedule(context, "с добрым утром", System.currentTimeMillis() - 1_000L)
        eventually("сообщение отложено") { ChatSync.state.value.scheduled.size == 1 }
        runBlocking { ChatSync.releaseDue(context) }
        eventually("сообщение ушло") { aizatHead()?.messages?.any { it.text == "с добрым утром" } == true }
        assertTrue(ChatSync.state.value.scheduled.isEmpty())
    }

    @Test
    fun sharedListIsTheSameOnBothPhones() {
        SharedListStore.update(context.filesDir) { emptyList() }
        SharedListStore.update(context.filesDir) { SharedList.add(it, "milk", "молоко", Person.AIZAT, System.currentTimeMillis()) }
        assertTrue(runBlocking { PartnerSync.sync(context) })
        val theirCopy = PartnerShare.decode(nastyaShares.fetch(code, Person.AIZAT)!!.payload)!!.list
        assertEquals(listOf("молоко"), theirCopy.map { it.text })
        // Настя отметила «купила» и добавила своё.
        val now = System.currentTimeMillis() + 1_000
        val hers = SharedList.add(SharedList.toggle(theirCopy, "milk", now), "bread", "хлеб", Person.NASTYA, now)
        nastyaShares.publish(code, Person.NASTYA, PartnerShare.encode(SharedEvents(Person.NASTYA, now, emptyList(), list = hers)), now)
        assertTrue(runBlocking { PartnerSync.sync(context) })
        val mine = SharedListStore.current(context.filesDir)
        assertTrue(mine.first { it.id == "milk" }.done)
        assertEquals(listOf("молоко", "хлеб"), mine.map { it.text })
        SharedListStore.update(context.filesDir) { emptyList() }
    }

    @Test
    fun greetingDropTravelsBothWays() {
        val settings = AppSettings(context)
        val sent = System.currentTimeMillis()
        settings.sentDrops = listOf(sent)
        assertTrue("обмен не удался: ${PartnerSync.info.value.problem}", runBlocking { PartnerSync.sync(context) })
        val mine = PartnerShare.decode(nastyaShares.fetch(code, Person.AIZAT)!!.payload)!!
        assertEquals(listOf(sent), mine.drops)

        val dropAt = System.currentTimeMillis() - 60_000
        val now = System.currentTimeMillis()
        nastyaShares.publish(code, Person.NASTYA, PartnerShare.encode(SharedEvents(Person.NASTYA, now, emptyList(), emptyList(), listOf(dropAt))), now)
        assertTrue(runBlocking { PartnerSync.sync(context) })
        val shared = PartnerEventsRepository.current(context.filesDir)!!
        assertEquals(Person.NASTYA, shared.from)
        assertEquals(listOf(dropAt), shared.drops)
        assertEquals(listOf(dropAt), GreetingDrops.unseen(shared.drops, settings.dropsSeenUntil, System.currentTimeMillis()))
    }
}
