package io.github.cmix7777.kazhdyidnevnik

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import io.github.cmix7777.kazhdyidnevnik.ui.GreetingArrival
import io.github.cmix7777.kazhdyidnevnik.ui.GreetingDropLayer
import io.github.cmix7777.kazhdyidnevnik.ui.GreetingDropState
import io.github.cmix7777.kazhdyidnevnik.ui.greetingDropSource
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cmix7777.kazhdyidnevnik.data.DayItem
import io.github.cmix7777.kazhdyidnevnik.data.DayStrip
import io.github.cmix7777.kazhdyidnevnik.data.Lesson
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.WorkShift
import io.github.cmix7777.kazhdyidnevnik.ui.Accents
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.DayStripView
import io.github.cmix7777.kazhdyidnevnik.ui.GlassCard
import io.github.cmix7777.kazhdyidnevnik.ui.LocalAccent
import io.github.cmix7777.kazhdyidnevnik.ui.LocalDark
import io.github.cmix7777.kazhdyidnevnik.ui.Palette
import io.github.cmix7777.kazhdyidnevnik.ui.PersonTheme
import io.github.cmix7777.kazhdyidnevnik.ui.Pill
import io.github.cmix7777.kazhdyidnevnik.ui.SectionTitle
import io.github.cmix7777.kazhdyidnevnik.ui.TwoToneTitle
import io.github.cmix7777.kazhdyidnevnik.ui.fx.DropRefreshIndicator
import io.github.cmix7777.kazhdyidnevnik.ui.fx.InkOrigin
import io.github.cmix7777.kazhdyidnevnik.ui.fx.dropShift
import io.github.cmix7777.kazhdyidnevnik.ui.fx.inkPage
import io.github.cmix7777.kazhdyidnevnik.ui.fx.inkTouchOrigin
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberDropRefresh
import io.github.cmix7777.kazhdyidnevnik.ui.fx.rememberEffectTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Кадры живого интерфейса: капля обновления, полоска дня и перелив между страницами. */
@RunWith(AndroidJUnit4::class)
class LiveUiShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Dark(person: Person = Person.AIZAT, content: @Composable () -> Unit) {
        AppTheme {
            CompositionLocalProvider(LocalDark provides true) {
                PersonTheme(person, content)
            }
        }
    }

    @Composable
    private fun FakePage(title: String, modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Pill(text = "Сегодня", accent = true)
            TwoToneTitle(first = title, second = "12 октября")
            repeat(4) { index ->
                GlassCard {
                    Text("Пара ${index + 1}: Математика", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Text("ауд. 312, с 9:00 до 10:30", color = Palette.TextMuted)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun dropRefresh() {
        val fraction = mutableFloatStateOf(0.3f)
        val refreshing = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Dark {
                val accent = LocalAccent.current
                val state = rememberPullToRefreshState()
                val drop = rememberDropRefresh(refreshing.value, failed = false)
                LaunchedEffect(fraction.floatValue) { state.snapTo(fraction.floatValue) }
                // Как будто обновление начато жестом: тогда в конце рисуется галочка.
                LaunchedEffect(refreshing.value) { if (refreshing.value) drop.pulled() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.background),
                ) {
                    FakePage("Понедельник", Modifier.dropShift(state))
                    DropRefreshIndicator(
                        state = state,
                        drop = drop,
                        refreshing = refreshing.value,
                        colorTop = accent.primary,
                        colorBottom = accent.light,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        save("drop_1_pull_small")
        fraction.floatValue = 0.8f
        compose.mainClock.advanceTimeBy(100)
        save("drop_2_pull")
        fraction.floatValue = 1.5f
        compose.mainClock.advanceTimeBy(100)
        save("drop_3_pull_far")
        fraction.floatValue = 1f
        refreshing.value = true
        compose.mainClock.advanceTimeBy(200)
        save("drop_4_detach")
        compose.mainClock.advanceTimeBy(700)
        save("drop_5_spin")
        refreshing.value = false
        compose.mainClock.advanceTimeBy(450)
        save("drop_6_check")
        compose.mainClock.advanceTimeBy(700)
        fraction.floatValue = 0.4f
        compose.mainClock.advanceTimeBy(100)
        save("drop_7_hide")
    }

    @Test
    fun dayStrip() {
        val date = LocalDate.of(2026, 10, 12)
        fun lesson(subject: String, from: Int, to: Int) =
            DayItem.LessonItem(Lesson(date, 1, LocalTime.ofSecondOfDay(from * 60L), LocalTime.ofSecondOfDay(to * 60L), subject, "", "", "312", false))
        val items = listOf(
            lesson("Математика", 540, 630),
            lesson("История", 640, 730),
            DayItem.WorkItem(WorkShift(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(20, 0))),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Dark {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAccent.current.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    for (time in listOf(LocalTime.of(8, 20), LocalTime.of(9, 50), LocalTime.of(12, 35), LocalTime.of(17, 10))) {
                        SectionTitle("Расписание", accent = "9:00–20:00")
                        DayStripView(DayStrip.build(items, time)!!, time, rememberEffectTime(20))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        save("strip_1")
    }

    @Test
    fun inkSwipe() {
        val origin = InkOrigin()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Dark {
                val pager = rememberPagerState { 2 }
                // Как в приложении: страницы прозрачные, фон общий. Так видно, не просвечивают ли они друг через друга.
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAccent.current.background)
                        .inkTouchOrigin(origin),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val person = if (page == 0) Person.AIZAT else Person.NASTYA
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .inkPage(pager, page, origin, Accents.of(Person.AIZAT, true).primary, Accents.of(Person.NASTYA, true).primary),
                    ) {
                        PersonTheme(person) {
                            FakePage(title = person.shortName)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        // Вперёд: палец справа ведём влево и держим.
        compose.onRoot().performTouchInput { down(Offset(width * 0.85f, height * 0.55f)) }
        compose.onRoot().performTouchInput { moveBy(Offset(-width * 0.3f, 0f)) }
        compose.mainClock.advanceTimeBy(50)
        save("ink_1_forward_30")
        compose.onRoot().performTouchInput { moveBy(Offset(-width * 0.25f, 0f)) }
        compose.mainClock.advanceTimeBy(50)
        save("ink_2_forward_55")
        compose.onRoot().performTouchInput { moveBy(Offset(-width * 0.2f, 0f)) }
        compose.mainClock.advanceTimeBy(50)
        save("ink_3_forward_75")
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        save("ink_4_second_page")
        // Назад: палец слева ведём вправо.
        compose.onRoot().performTouchInput { down(Offset(width * 0.15f, height * 0.4f)) }
        compose.onRoot().performTouchInput { moveBy(Offset(width * 0.45f, 0f)) }
        compose.mainClock.advanceTimeBy(50)
        save("ink_5_back_45")
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        save("ink_6_first_page")
    }

    @Test
    fun greetingSend() {
        var sent = 0L
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Dark {
                val scope = rememberCoroutineScope()
                val state = remember { GreetingDropState(scope, send = { sent = it }, cancelSend = { sent = 0L }) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAccent.current.background),
                ) {
                    FakePage("Понедельник", Modifier.padding(top = 70.dp))
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(20.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(Palette.Chip),
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text("Айзат", color = Palette.Text)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("partner")
                                .greetingDropSource(state, enabled = true, onTap = {}),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Настя", color = Palette.TextMuted)
                        }
                    }
                    GreetingDropLayer(state, Person.AIZAT)
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("partner").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(900)
        save("greet_1_growing")
        compose.onNodeWithTag("partner").performTouchInput { moveBy(Offset(-40f, 420f)) }
        compose.mainClock.advanceTimeBy(600)
        save("greet_2_held")
        compose.onNodeWithTag("partner").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(260)
        save("greet_3_fly")
        compose.mainClock.advanceTimeBy(600)
        save("greet_4_undo")
        compose.mainClock.advanceTimeBy(3_300)
        check(sent > 0L) { "Капля не отправилась" }
        save("greet_5_sent")
    }

    @Test
    fun greetingArrival() {
        val today = LocalDate.of(2026, 10, 12)
        fun at(hour: Int, minute: Int) =
            LocalDateTime.of(today, LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Dark(Person.NASTYA) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAccent.current.background),
                ) {
                    FakePage("Понедельник")
                    GreetingArrival(Person.AIZAT, listOf(at(9, 10), at(14, 20), at(18, 5)), today, onDone = {})
                }
            }
        }
        var elapsed = 0L
        for (ms in listOf(400L, 800L, 1_000L, 1_700L, 2_800L, 4_000L, 4_700L)) {
            compose.mainClock.advanceTimeBy(ms - elapsed)
            elapsed = ms
            save("arrive_%04d".format(ms))
        }
    }

    @Test
    fun greetingArrivalSingle() {
        val today = LocalDate.of(2026, 10, 12)
        val at = LocalDateTime.of(today, LocalTime.of(14, 20)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Dark(Person.AIZAT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalAccent.current.background),
                ) {
                    FakePage("Понедельник")
                    GreetingArrival(Person.NASTYA, listOf(at), today, onDone = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_900)
        save("arrive_single")
    }
}
