package io.github.cmix7777.kazhdyidnevnik

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.cmix7777.kazhdyidnevnik.data.Lesson
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleSource
import io.github.cmix7777.kazhdyidnevnik.data.WeekSchedule
import io.github.cmix7777.kazhdyidnevnik.data.weekStartFor
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import io.github.cmix7777.kazhdyidnevnik.notify.ScheduleSync
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * Проверка расписания с подставным сайтом: первая загрузка, изменения с уведомлением
 * и защита от «пустой недели», которую сайт иногда отдаёт по ошибке.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleSyncFlowTest {

    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private val monday: LocalDate = weekStartFor(LocalDate.of(2026, 11, 9))

    /** Сайт отдаёт то, что лежит в [next]; сохранённые недели — в памяти. */
    private val site = object : ScheduleSource {
        var next: List<Lesson> = emptyList()
        val saved = mutableMapOf<LocalDate, WeekSchedule>()
        override suspend fun download(skip: Int, today: LocalDate, timeoutMs: Int): WeekSchedule =
            WeekSchedule(weekStartFor(today).plusWeeks(skip.toLong()), next, System.currentTimeMillis())

        override fun loadCached(weekStart: LocalDate): WeekSchedule? = saved[weekStart]

        override fun save(week: WeekSchedule) {
            saved[week.weekStart] = week
        }
    }

    private fun lesson(day: Long, hour: Int, subject: String) = Lesson(
        date = monday.plusDays(day),
        number = 1,
        start = LocalTime.of(hour, 0),
        end = LocalTime.of(hour + 1, 30),
        subject = subject,
        topic = "",
        teacher = "Иванов И.И.",
        room = "101",
        online = false,
    )

    private fun sync() = runBlocking { ScheduleSync.syncWeek(context, Person.AIZAT, 0, today = monday) }

    private fun changeNotifications() = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.count { it.notification.channelId == "schedule_changes" }

    @Before
    fun setUp() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettings(context).owner = Person.AIZAT
        ScheduleSync.sourceOverride = { _, _ -> site }
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @After
    fun tearDown() {
        ScheduleSync.sourceOverride = null
        context.getSystemService(NotificationManager::class.java).cancelAll()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun firstLoadIsSavedQuietlyAndChangeIsNotified() {
        site.next = listOf(lesson(1, 9, "Математика"), lesson(3, 12, "Физика"))
        val first = sync()
        assertEquals(2, first.week.lessons.size)
        assertTrue(first.changes.isEmpty())
        assertEquals(0, changeNotifications())

        // Физику перенесли на 14:00 — это изменение, о нём приходит уведомление.
        site.next = listOf(lesson(1, 9, "Математика"), lesson(3, 14, "Физика"))
        val second = sync()
        assertTrue(second.changes.isNotEmpty())
        Thread.sleep(500)
        assertEquals(1, changeNotifications())
    }

    @Test
    fun emptyWeekFromTheSiteIsBelievedOnlyTheSecondTime() {
        site.next = listOf(lesson(2, 9, "Математика"))
        sync()
        site.next = emptyList()
        // Первый раз пустая неделя похожа на сбой сайта: остаётся прежняя.
        assertEquals(1, sync().week.lessons.size)
        // Второй раз подряд — значит, правда пар нет.
        assertEquals(0, sync().week.lessons.size)
    }
}
