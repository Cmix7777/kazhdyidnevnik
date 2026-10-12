package io.github.cmix7777.kazhdyidnevnik

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.cmix7777.kazhdyidnevnik.data.Deadline
import io.github.cmix7777.kazhdyidnevnik.data.Occasion
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.SpecialKind
import io.github.cmix7777.kazhdyidnevnik.data.SpecialToday
import io.github.cmix7777.kazhdyidnevnik.ui.AppTheme
import io.github.cmix7777.kazhdyidnevnik.ui.SpecialDayOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** Кадры анимаций особых дней поверх простого экрана: годовщина, день рождения (цифра и сердце), Новый год. */
@RunWith(AndroidJUnit4::class)
class SpecialDayShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 12)

    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(name: String, special: SpecialToday, times: List<Long>) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0610))) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Сегодня", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        repeat(8) { Text("Строка экрана под анимацией ${it + 1}", color = Color(0xFFA79DBA)) }
                    }
                    SpecialDayOverlay(special, today, onDone = {})
                }
            }
        }
        var elapsed = 0L
        for (ms in times) {
            compose.mainClock.advanceTimeBy(ms - elapsed)
            elapsed = ms
            save("%s_%04d".format(name, ms))
        }
    }

    @Test
    fun anniversary() {
        val date = Deadline(title = "", date = LocalDate.of(2023, 10, 12), id = "a", occasion = Occasion.ANNIVERSARY)
        shoot("anniv", SpecialToday(SpecialKind.ANNIVERSARY, date, "k"), listOf(500, 1200, 1700, 2400, 2900, 3700))
    }

    @Test
    fun birthdayWithAge() {
        val date = Deadline(title = "", date = today.minusYears(20), id = "b", occasion = Occasion.BIRTHDAY, whose = Person.NASTYA)
        shoot("bday", SpecialToday(SpecialKind.BIRTHDAY, date, "k"), listOf(700, 1500, 2200, 3050, 3800))
    }

    @Test
    fun birthdayWithHeart() {
        val date = Deadline(title = "", date = today, id = "h", occasion = Occasion.BIRTHDAY, whose = Person.AIZAT, yearKnown = false)
        shoot("heart", SpecialToday(SpecialKind.BIRTHDAY, date, "k"), listOf(2200))
    }

    @Test
    fun newYear() {
        shoot("ny", SpecialToday(SpecialKind.NEW_YEAR, null, "k"), listOf(800, 2400, 3300, 4500))
    }
}
