package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.CloudShares
import io.github.cmix7777.kazhdyidnevnik.data.PairCode
import io.github.cmix7777.kazhdyidnevnik.data.PartnerEventsRepository
import io.github.cmix7777.kazhdyidnevnik.data.PartnerShare
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.SharedEvents
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.CloudNet
import io.github.cmix7777.kazhdyidnevnik.service.JsoupTransport
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import io.github.cmix7777.kazhdyidnevnik.testing.FakeFirestore
import io.github.cmix7777.kazhdyidnevnik.testing.MemoryTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Связь телефонов от начала до конца, с подставным облаком: Айзат уже создал код и отправил его,
 * Настя вставляет сообщение с кодом в настройках — и дела Айзата приходят к ней сами. Потом — отвязка.
 */
@RunWith(AndroidJUnit4::class)
class PairingFlowUiTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var store: FakeFirestore

    @Before
    fun setUp() {
        store = FakeFirestore()
        CloudNet.transport = store
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        ChatSync.resetForTest(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
    }

    @After
    fun tearDown() {
        scenario?.close()
        CloudNet.transport = JsoupTransport
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        ChatSync.resetForTest(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
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
            throw AssertionError("Нужное не появилось. Сейчас на экране: ${screenTexts()}", e)
        }
    }

    private fun scrollToItem(text: String, substring: Boolean = false) {
        var last: Throwable? = null
        try {
            compose.waitUntil(20_000) {
                val lists = compose.onAllNodes(
                    hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
                )
                val count = lists.fetchSemanticsNodes().size
                for (index in 0 until count) {
                    try {
                        lists[index].performScrollToNode(hasText(text, substring = substring))
                        return@waitUntil true
                    } catch (e: Throwable) {
                        last = e
                    }
                }
                false
            }
        } catch (e: Throwable) {
            throw AssertionError("Не нашёл на экране «$text»: ${last?.message}. Сейчас на экране: ${screenTexts()}", e)
        }
    }

    private fun putOnClipboard(text: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("test", text))
        }
    }

    @Test
    fun nastyaPastesAizatsCodeAndHisPlansArrive() {
        // Айзат создал код, его дела уже лежат в облаке под этим кодом.
        val code = PairCode.generate()
        val now = System.currentTimeMillis()
        CloudShares(store, MemoryTokens()).publish(
            code,
            Person.AIZAT,
            PartnerShare.encode(SharedEvents(Person.AIZAT, now, emptyList())),
            now,
        )

        AppSettings(context).owner = Person.NASTYA
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasContentDescription("Настройки"), 30_000)
        compose.onNodeWithContentDescription("Настройки").performClick()
        waitFor(hasText("Чей это телефон"))

        // Буфер обмена на эмуляторе иногда отдаёт пустоту: кладём и вставляем до трёх раз.
        repeat(3) {
            if (AppSettings(context).pairCode == code) return@repeat
            putOnClipboard(PairCode.message(Person.AIZAT, code))
            compose.waitForIdle()
            scrollToItem("Вставить код")
            compose.onNodeWithText("Вставить код").performClick()
            runCatching { compose.waitUntil(5_000) { AppSettings(context).pairCode == code } }
        }
        compose.waitUntil(20_000) { AppSettings(context).pairCode == code }
        // Связь появилась, и обмен с облаком прошёл: дела Айзата у Насти.
        scrollToItem("Телефоны связаны", substring = true)
        compose.waitUntil(20_000) { AppSettings(context).partnerHeardState == "ok" }
        assertEquals(now, PartnerEventsRepository.current(context.filesDir)?.at)

        // Её дела тоже ушли в облако: Айзат их увидит.
        compose.waitUntil(20_000) { runCatching { CloudShares(store, MemoryTokens()).fetch(code, Person.NASTYA) }.getOrNull() != null }

        // Отвязка: код забыт, дальше ничего не уходит.
        scrollToItem("Отвязать")
        compose.onNodeWithText("Отвязать").performClick()
        compose.waitUntil(10_000) { AppSettings(context).pairCode == null }
        assertEquals(null, AppSettings(context).pairCode)
    }
}
