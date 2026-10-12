package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.ThemeMode
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверка экранов на настоящем эмуляторе: приложение запускается с чистого листа, как после установки,
 * и тесты нажимают на то же, что нажимает человек. Сеть не нужна: расписание и погода могут не загрузиться,
 * тесты на это не опираются.
 */
@RunWith(AndroidJUnit4::class)
class AppScreensTest {

    @get:Rule(order = 0)
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun cleanSlate() {
        // Как будто приложение только что установили: ни владельца телефона, ни связи, ни темы.
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    // ---- помощники ----

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun launchAs(person: Person) {
        AppSettings(context).owner = person
        launch()
    }

    /** Все надписи, которые сейчас есть на экране (включая окна поверх него): нужны, чтобы понять причину сбоя. */
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
     * Нажать «+ Добавить дело» и дождаться окна. Пока грузятся погода и расписание, список под пальцем
     * может сдвинуться, поэтому при промахе прокручиваем и нажимаем ещё раз (до трёх попыток).
     */
    private fun openNewEventEditor() {
        var lastScreen = ""
        repeat(3) {
            scrollToItem("+ Добавить дело")
            compose.onNodeWithText("+ Добавить дело").performClick()
            val opened = try {
                compose.waitUntil(8_000) { compose.onAllNodes(hasText("Новое дело")).fetchSemanticsNodes().isNotEmpty() }
                true
            } catch (e: ComposeTimeoutException) {
                false
            }
            if (opened) return
            lastScreen = screenTexts()
        }
        throw AssertionError("Окно «Новое дело» не открылось. Сейчас на экране: $lastScreen")
    }

    private fun waitForText(text: String, substring: Boolean = false) = waitFor(hasText(text, substring = substring))

    private fun waitDisplayed(text: String, timeoutMs: Long = 20_000) {
        compose.waitUntil(timeoutMs) {
            try {
                compose.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    /**
     * Прокрутить тот из списков экрана, где есть нужная строка, чтобы она оказалась на виду.
     * Списки ленивые: пока строка далеко за экраном, её в дереве нет, поэтому пробуем все списки и ждём.
     * Берём только вертикальные списки: горизонтальный листатель страниц тоже умеет «прокрутить к строке»,
     * но вниз он не листает, и строка могла остаться под краем экрана.
     */
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
            throw AssertionError("Не нашёл на экране «$text» ни в одном списке: ${last?.message}", e)
        }
    }

    private fun openSettings() {
        waitFor(hasContentDescription("Настройки"))
        compose.onNodeWithContentDescription("Настройки").performClick()
        waitForText("Чей это телефон")
    }

    private fun putOnClipboard(text: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val manager = context.getSystemService(ClipboardManager::class.java)
            manager.setPrimaryClip(ClipData.newPlainText("test", text))
        }
    }

    // ---- первый запуск ----

    @Test
    fun firstLaunchAsksWhoseThePhoneIs() {
        launch()
        waitDisplayed("Айзат")
        compose.onNodeWithText("Айзат").assertIsDisplayed()
        compose.onNodeWithText("Настя").assertIsDisplayed()
        compose.onNodeWithText("МВЕК, ДИС-234/21Б").assertIsDisplayed()
        compose.onNodeWithText("Колледж УдГУ, торговое дело").assertIsDisplayed()
    }

    @Test
    fun choosingAPhoneOpensTheDiary() {
        launch()
        waitDisplayed("Настя")
        compose.onNodeWithText("Настя").performClick()
        // Появились нижние вкладки, и выбор владельца запомнился.
        waitFor(hasText("Бюджет") and hasClickAction())
        waitFor(hasText("Неделя") and hasClickAction())
        waitFor(hasText("Даты") and hasClickAction())
        waitFor(hasText("Прогресс") and hasClickAction())
        assertEquals(Person.NASTYA, AppSettings(context).owner)
    }

    // ---- нижние вкладки ----

    @Test
    fun bottomTabsOpenTheirScreens() {
        launchAs(Person.AIZAT)
        for ((tab, marker) in listOf("Бюджет" to "Остаток", "Даты" to "Что впереди", "Прогресс" to "Как идёт")) {
            waitFor(hasText(tab) and hasClickAction())
            compose.onAllNodes(hasText(tab) and hasClickAction()).onLast().performClick()
            waitForText(marker, substring = true)
        }
    }

    // ---- свои дела ----

    @Test
    fun addedEventShowsUpInTodayList() {
        launchAs(Person.AIZAT)
        openNewEventEditor()
        compose.onNodeWithText("Название (можно не писать)").performTextInput("Автотест экрана")
        compose.onNodeWithText("Сохранить").performClick()

        // Окно закрылось, дело появилось в списке на сегодня.
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Новое дело")).fetchSemanticsNodes().isEmpty() }
        scrollToItem("Автотест экрана", substring = true)
        try {
            compose.onNodeWithText("Автотест экрана", substring = true).assertIsDisplayed()
        } catch (e: AssertionError) {
            // Подробности для разбора: где этот узел, размещён ли он, что на экране, и снимок экрана.
            val nodes = compose.onAllNodes(hasText("Автотест экрана", substring = true)).fetchSemanticsNodes()
            val where = nodes.joinToString { "${it.boundsInRoot} placed=${it.layoutInfo.isPlaced}" }
            runCatching {
                val dir = java.io.File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.FileOutputStream(java.io.File(dir, "fail_added_event.png")).use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            throw AssertionError("${e.message} || Узлы: $where || Экран: ${screenTexts()}", e)
        }
    }

    @Test
    fun cancelledEditorLeavesNothingBehind() {
        launchAs(Person.AIZAT)
        openNewEventEditor()
        compose.onNodeWithText("Название (можно не писать)").performTextInput("Это не должно сохраниться")
        compose.onNodeWithText("Отмена").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Новое дело")).fetchSemanticsNodes().isEmpty() }
        assertEquals(0, compose.onAllNodes(hasText("Это не должно сохраниться", substring = true)).fetchSemanticsNodes().size)
    }

    // ---- настройки ----

    @Test
    fun settingsOffersTheLinkWithThePartner() {
        launchAs(Person.AIZAT)
        openSettings()
        scrollToItem("Связь с Настей")
        compose.onNodeWithText("Связь с Настей").assertIsDisplayed()
        scrollToItem("Создать код и отправить")
        compose.onNodeWithText("Создать код и отправить").assertIsDisplayed()
        compose.onNodeWithText("Вставить код").assertIsDisplayed()
    }

    @Test
    fun themeSwitchChangesAndRemembersTheTheme() {
        launchAs(Person.AIZAT)
        openSettings()
        scrollToItem("Светлая")
        compose.onNodeWithText("Светлая").performClick()
        compose.waitUntil(10_000) { AppSettings(context).themeMode == ThemeMode.LIGHT }
        scrollToItem("Тёмная")
        compose.onNodeWithText("Тёмная").performClick()
        compose.waitUntil(10_000) { AppSettings(context).themeMode == ThemeMode.DARK }
    }

    @Test
    fun pastingNothingUsefulIsExplained() {
        launchAs(Person.AIZAT)
        openSettings()
        putOnClipboard("привет, это просто текст")
        scrollToItem("Вставить код")
        compose.onNodeWithText("Вставить код").performClick()
        waitForText("В буфере нет кода", substring = true)
    }

    @Test
    fun pastingAnOldStyleCodeIsExplained() {
        launchAs(Person.AIZAT)
        openSettings()
        putOnClipboard("Код связи Каждогодневника (Настя): kdp-abcde-fghjk-mnpqr-stuvw")
        scrollToItem("Вставить код")
        compose.onNodeWithText("Вставить код").performClick()
        try {
            waitForText("Это код старого вида", substring = true)
        } catch (e: AssertionError) {
            var inClipboard = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                inClipboard = context.getSystemService(ClipboardManager::class.java)
                    .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            }
            throw AssertionError("${e.message} || В буфере: «${inClipboard.take(80)}»", e)
        }
        // Старый код не подключился.
        assertEquals(null, AppSettings(context).pairCode)
    }

    @Test
    fun phoneLinkedBeforeEncryptionAsksToLinkAgain() {
        // Телефон, который был связан по старому коду (до шифрования).
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("pairCode", "abcdefghjkmnpqrstuvw").commit()
        launchAs(Person.AIZAT)
        openSettings()
        scrollToItem("После обновления связь нужно создать заново", substring = true)
        compose.onNodeWithText("После обновления связь нужно создать заново", substring = true).assertIsDisplayed()
        scrollToItem("Создать код и отправить")
        compose.onNodeWithText("Создать код и отправить").assertIsDisplayed()
    }

    // ---- страница второго человека ----

    @Test
    fun swipingToThePartnerShowsTheirPage() {
        launchAs(Person.AIZAT)
        waitFor(hasText("Настя") and hasClickAction())
        compose.onAllNodes(hasText("Настя") and hasClickAction()).onFirst().performClick()
        waitDisplayed("Сегодня у Насти")
    }
}
