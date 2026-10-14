package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.PairCode
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import io.github.cmix7777.kazhdyidnevnik.ui.DropOutbox
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Капля привета в настоящем приложении: удержание имени Насти в переключателе сверху.
 * Связь включается уже после запуска и капля отменяется до отправки, поэтому в облако тест ничего не пишет.
 */
@RunWith(AndroidJUnit4::class)
class GreetingRealAppTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettings(context).owner = Person.AIZAT
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { DropOutbox.cancel() }
        scenario?.close()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
    }

    private fun save(name: String) {
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Настя") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun partnerName() = compose.onAllNodes(hasText("Настя") and hasClickAction()).onFirst()

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun holdingThePartnerNameGrowsAndThrowsADrop() {
        launch()
        // Телефоны «связаны» уже после запуска: обмен при старте не шёл, и в облако ничего не уходит.
        AppSettings(context).pairCode = PairCode.generate()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PartnerSync.load(context) }
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        partnerName().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_400)
        save("real_greet_1_hold")
        partnerName().performTouchInput { moveBy(Offset(0f, 300f)) }
        compose.mainClock.advanceTimeBy(100)
        partnerName().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(900)
        val sent = has("Капля полетела к Насте")
        // Отменяем сразу, пока не прошли три секунды: капля никуда не уходит.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { DropOutbox.cancel() }
        save("real_greet_2_undo")
        check(sent) { "Капля не полетела: плашки «Капля полетела к Насте» нет" }
    }

    @Test
    fun withoutLinkHoldingExplainsWhatToDo() {
        launch()
        compose.mainClock.autoAdvance = false
        partnerName().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        partnerName().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        save("real_greet_3_hint")
        check(has("Капли ходят, когда телефоны связаны")) { "Нет подсказки, что телефоны не связаны" }
    }
}
