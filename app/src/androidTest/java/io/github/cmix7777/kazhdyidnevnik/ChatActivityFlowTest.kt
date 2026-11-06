package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.ChatCloud
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.CloudNet
import io.github.cmix7777.kazhdyidnevnik.service.JsoupTransport
import io.github.cmix7777.kazhdyidnevnik.testing.FakeFirestore
import io.github.cmix7777.kazhdyidnevnik.testing.MemoryTokens
import io.github.cmix7777.kazhdyidnevnik.ui.ChatActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Настоящий экран переписки с подставным облаком: открыть, прочитать, написать, получить ответ,
 * длинная история с архивом и переписка после сброса нумерации у второго телефона (то, на чём раньше вылетало).
 */
@RunWith(AndroidJUnit4::class)
class ChatActivityFlowTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private val code = "abcdefghjkmnpqrstuvw"
    private var scenario: ActivityScenario<ChatActivity>? = null
    private lateinit var store: FakeFirestore
    private lateinit var nastya: ChatCloud
    private var partner = ChatState(restored = true)

    @Before
    fun setUp() {
        store = FakeFirestore()
        CloudNet.transport = store
        ChatSync.screenSeen = { true }
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettings(context).owner = Person.AIZAT
        AppSettings(context).pairCode = code
        ChatSync.resetForTest(context)
        nastya = ChatCloud(store, MemoryTokens())
        partner = ChatState(restored = true)
    }

    @After
    fun tearDown() {
        scenario?.close()
        ChatSync.resetForTest(context)
        CloudNet.transport = JsoupTransport
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun partnerSends(vararg texts: String, at: Long? = null) {
        var time = at ?: System.currentTimeMillis()
        for (text in texts) {
            partner = ChatLogic.append(partner, Person.NASTYA, text, time)
            time += 1_000
        }
        val now = System.currentTimeMillis()
        val plan = ChatLogic.planUpload(partner, Person.NASTYA, push = null, hook = null)
        val version = maxOf(now, partner.myVersion + 1)
        nastya.upload(code, Person.NASTYA, plan.pages, plan.head, version)
        partner = ChatLogic.afterUpload(partner, plan, version, partner.revision)
    }

    private fun openChat() {
        scenario = ActivityScenario.launch(ChatActivity::class.java)
        waitFor(hasTestTag("chat-input"), 20_000)
    }

    private fun screenTexts(): String =
        compose.onAllNodes(SemanticsMatcher("любой узел") { true }).fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } }
            .filter { it.isNotBlank() }
            .map { if (it.length > 60) it.take(60) + "…" else it }
            .distinct()
            .joinToString(" | ")
            .take(900)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 20_000) {
        try {
            compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Нужное не появилось. Сейчас на экране: ${screenTexts()}. Облако: ${ChatSync.status.value.problem}", e)
        }
    }

    private fun waitUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(200)
        }
        throw AssertionError("Не дождался: $what. Сейчас на экране: ${screenTexts()}")
    }

    @Test
    fun readWriteAndGetAnAnswer() {
        partnerSends("привет, как ты?")
        openChat()
        waitFor(hasText("привет, как ты?"))
        // Переписка на экране — второй телефон видит «прочитано».
        waitUntil("прочитано у Насти") { nastya.mark(code, Person.AIZAT)?.read == 1L }

        compose.onNodeWithTag("chat-input").performTextInput("всё хорошо")
        compose.onNodeWithTag("chat-send").performClick()
        waitUntil("сообщение у Насти") {
            nastya.head(code, Person.AIZAT)?.first?.messages?.any { it.text == "всё хорошо" } == true
        }
        waitFor(hasText("всё хорошо"))

        partnerSends("ура")
        waitFor(hasText("ура"), 20_000)
        waitUntil("ответ тоже прочитан") { nastya.mark(code, Person.AIZAT)?.read == 2L }
    }

    @Test
    fun longHistoryWithArchiveOpensAndScrollsToTheStart() {
        // Больше, чем влезает в голову: часть уходит в архивные страницы, телефон дочитывает их сам.
        partnerSends(*Array(ChatLogic.HEAD_LIMIT_COUNT + 30) { "сообщение номер $it" })
        openChat()
        waitFor(hasText("сообщение номер ${ChatLogic.HEAD_LIMIT_COUNT + 29}"), 30_000)
        waitUntil("вся история на телефоне") { ChatSync.state.value.theirs.size == ChatLogic.HEAD_LIMIT_COUNT + 30 }
        val rows = ChatLogic.rows(ChatSync.state.value, Person.AIZAT).size
        compose.onNodeWithTag("chat-list").performScrollToIndex(rows - 1)
        waitFor(hasText("сообщение номер 0"), 10_000)
    }

    @Test
    fun chatAfterPartnerStartedOverOpensWithoutCrash() {
        val hour = 3_600_000L
        val now = System.currentTimeMillis()
        // Старая нумерация: вчера и сегодня.
        partnerSends("старое вчера", at = now - 26 * hour)
        partnerSends("старое сегодня", "третье старое", at = now - 2 * hour)
        openChat()
        waitFor(hasText("третье старое"))
        scenario?.close()
        // Второй телефон переустановили: нумерация с начала. Прежняя переписка остаётся выше, новое — внизу.
        partner = ChatState(restored = true)
        partnerSends("я переустановила", at = now)
        openChat()
        waitFor(hasText("я переустановила"), 20_000)
        waitFor(hasText("третье старое"))
        assertEquals(listOf("я переустановила"), ChatSync.state.value.theirs.map { it.text })
        assertEquals(3, ChatSync.state.value.earlier.size)
        // Новое сообщение прочитано, только когда переписка открыта: отметка у Насти — 1, а не старые 3.
        waitUntil("прочитано новое") { nastya.mark(code, Person.AIZAT)?.read == 1L }
    }
}
