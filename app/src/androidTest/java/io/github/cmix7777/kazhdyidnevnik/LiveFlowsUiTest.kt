package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.CloudShares
import io.github.cmix7777.kazhdyidnevnik.data.PartnerEventsRepository
import io.github.cmix7777.kazhdyidnevnik.data.PartnerShare
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleSource
import io.github.cmix7777.kazhdyidnevnik.data.SharedEvents
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.ScheduleSync
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.service.CloudNet
import io.github.cmix7777.kazhdyidnevnik.service.JsoupTransport
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import io.github.cmix7777.kazhdyidnevnik.testing.FakeFirestore
import io.github.cmix7777.kazhdyidnevnik.testing.MemoryTokens
import io.github.cmix7777.kazhdyidnevnik.ui.DropOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Живые сценарии в настоящем приложении с подставным облаком и подставными сайтами расписания:
 * капля привета от Насти и к Насте, обновление жестом (капля доходит до галочки) и зависший сайт
 * (капля не крутится вечно, а показывает, что случилось).
 */
@RunWith(AndroidJUnit4::class)
class LiveFlowsUiTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private val code = "abcdefghjkmnpqrstuvw"
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var store: FakeFirestore

    /** Сайт расписания: отвечает быстро, пока [hang] не включён; потом замирает в коде, который не прервать. */
    @Volatile
    private var hang = false

    private val site = object : ScheduleSource {
        override suspend fun download(skip: Int, today: LocalDate, timeoutMs: Int): WeekSchedule {
            if (hang) withContext(Dispatchers.IO) { Thread.sleep(20_000) } else delay(300)
            return WeekSchedule(weekStartFor(today).plusWeeks(skip.toLong()), emptyList(), System.currentTimeMillis())
        }

        override fun loadCached(weekStart: LocalDate): WeekSchedule? = null

        override fun save(week: WeekSchedule) = Unit
    }

    @Before
    fun setUp() {
        store = FakeFirestore()
        CloudNet.transport = store
        ScheduleSync.sourceOverride = { _, _ -> site }
        ScheduleSync.refreshLimitMs = 3_000
        hang = false
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        ChatSync.resetForTest(context)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { DropOutbox.cancel() }
        scenario?.close()
        ScheduleSync.sourceOverride = null
        ScheduleSync.refreshLimitMs = 60_000
        CloudNet.transport = JsoupTransport
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        ChatSync.resetForTest(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
    }

    private fun launchAs(person: Person, paired: Boolean = true) {
        val settings = AppSettings(context)
        settings.owner = person
        if (paired) settings.pairCode = code
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasText(person.partner.shortName) and hasClickAction(), 30_000)
        compose.waitForIdle()
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

    /**
     * Ждать условия, давая приложению жить: часы Compose в тестах идут, только пока тест ждёт через них,
     * поэтому простой Thread.sleep останавливал бы анимации (капля так и висела бы, не долетев).
     */
    private fun waitUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMs) { runCatching(condition).getOrDefault(false) }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Не дождался: $what. Сейчас на экране: ${screenTexts()}", e)
        }
    }

    /** Потянуть страницу вниз, как пальцем, чтобы обновить. */
    private fun pullDown() {
        compose.onRoot().performTouchInput {
            swipe(Offset(centerX, height * 0.28f), Offset(centerX, height * 0.75f), durationMillis = 600)
        }
    }

    @Test
    fun nastyaThrowsADropAndItReachesAizat() {
        launchAs(Person.NASTYA)
        val name = compose.onAllNodes(hasText(Person.AIZAT.shortName) and hasClickAction()).onFirst()
        compose.mainClock.autoAdvance = false
        name.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_400)
        name.performTouchInput { moveBy(Offset(0f, 300f)) }
        compose.mainClock.advanceTimeBy(100)
        name.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        // Через три секунды на отмену капля уходит в облако — её видит телефон Айзата.
        val aizatPhone = CloudShares(store, MemoryTokens())
        waitUntil("капля Насти в облаке", 20_000) {
            aizatPhone.fetch(code, Person.NASTYA)?.let { PartnerShare.decode(it.payload) }?.drops?.isNotEmpty() == true
        }
    }

    /** Айзат бросил каплю: она лежит в облаке. Возвращает время капли. */
    private fun aizatThrowsDrop(): Long {
        val now = System.currentTimeMillis()
        val dropAt = now - 30_000
        val shared = SharedEvents(Person.AIZAT, now, emptyList(), emptyList(), listOf(dropAt))
        CloudShares(store, MemoryTokens()).publish(code, Person.AIZAT, PartnerShare.encode(shared), now)
        return dropAt
    }

    /** Капля упала и запись показана до конца: приложение отметило её просмотренной. */
    private fun waitShown(dropAt: Long, timeoutMs: Long) =
        waitUntil("капля Айзата показана Насте", timeoutMs) { AppSettings(context).dropsSeenUntil == dropAt }

    @Test
    fun dropFromAizatIsShownWhenNastyaOpensTheApp() {
        val dropAt = aizatThrowsDrop()
        launchAs(Person.NASTYA)
        waitShown(dropAt, 30_000)
    }

    @Test
    fun dropArrivesWhileNastyaHasTheAppOpen() {
        ChatSync.partnerPollMs = 2_000L
        try {
            launchAs(Person.NASTYA)
            Thread.sleep(2_000)
            val dropAt = aizatThrowsDrop()
            // Без пуша: приложение само подтягивает капли, пока открыто (здесь — при каждом круге опроса).
            waitShown(dropAt, 50_000)
        } finally {
            ChatSync.partnerPollMs = 60_000L
        }
    }

    @Test
    fun pushAboutDropBringsItAtOnce() {
        ChatSync.partnerPollMs = 10 * 60_000L
        try {
            launchAs(Person.NASTYA)
            Thread.sleep(2_000)
            val dropAt = aizatThrowsDrop()
            ChatSync.onPush(context, mapOf("k" to "d", "f" to Person.AIZAT.id))
            // Пуш уже принёс каплю в телефон (без опроса); дальше только анимация, а она на эмуляторе медленная.
            assertEquals(listOf(dropAt), PartnerEventsRepository.current(context.filesDir)?.drops)
            waitShown(dropAt, 50_000)
        } finally {
            ChatSync.partnerPollMs = 60_000L
        }
    }

    @Test
    fun pullToRefreshEndsWithCheckMark() {
        launchAs(Person.AIZAT, paired = false)
        Thread.sleep(1_500)
        pullDown()
        waitFor(hasText("обновлено только что"), 15_000)
    }

    @Test
    fun stuckScheduleSiteStopsTheDropAndSaysSo() {
        launchAs(Person.AIZAT, paired = false)
        Thread.sleep(1_500)
        hang = true
        pullDown()
        // Сайт так и не ответил, но капля не крутится вечно: через отведённое время — понятная надпись.
        waitFor(hasText("не ответил за минуту", substring = true), 20_000)
    }
}
