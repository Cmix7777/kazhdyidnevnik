package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Кадры склейки совместной недели на эмуляторе: открываем «Неделю», жмём «вместе» и снимаем экран
 * в разные моменты анимации. Картинки сохраняются в папку приложения, сборка забирает их для просмотра.
 * На проверке ветки анимации эмулятора включены, иначе склейка показывается сразу без движения.
 */
@RunWith(AndroidJUnit4::class)
class TogetherWeekShotsTest {

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
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun save(name: String) {
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun gluingWeeksFrames() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Неделя") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Неделя") and hasClickAction()).onLast().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("вместе") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        save("glue_0000_week")

        compose.mainClock.autoAdvance = false
        compose.onAllNodes(hasText("вместе") and hasClickAction()).onFirst().performClick()
        var elapsed = 0L
        for (ms in listOf(100L, 250L, 400L, 520L, 600L, 680L, 760L, 860L, 1000L, 1200L, 1500L, 2000L)) {
            compose.mainClock.advanceTimeBy(ms - elapsed)
            elapsed = ms
            save("glue_%04d".format(ms))
        }
        compose.mainClock.advanceTimeBy(3_000)
        check(compose.onAllNodes(hasText("оба свободны", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            "Общая неделя не появилась"
        }
        save("glue_9999_settled")
    }
}
