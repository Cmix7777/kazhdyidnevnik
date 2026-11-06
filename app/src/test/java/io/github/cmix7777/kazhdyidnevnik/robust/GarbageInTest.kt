package io.github.cmix7777.kazhdyidnevnik.robust

import io.github.cmix7777.kazhdyidnevnik.data.Backup
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatState
import io.github.cmix7777.kazhdyidnevnik.data.CloudCrypto
import io.github.cmix7777.kazhdyidnevnik.data.CollegeParser
import io.github.cmix7777.kazhdyidnevnik.data.DiaryCodec
import io.github.cmix7777.kazhdyidnevnik.data.MoneyFormat
import io.github.cmix7777.kazhdyidnevnik.data.PairCode
import io.github.cmix7777.kazhdyidnevnik.data.PartnerShare
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.ReleaseParser
import io.github.cmix7777.kazhdyidnevnik.data.SharedEvents
import io.github.cmix7777.kazhdyidnevnik.data.TimeoParser
import io.github.cmix7777.kazhdyidnevnik.data.WeatherParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Всё, что приходит снаружи (облако, файлы на телефоне, сайты, буфер обмена), может оказаться мусором:
 * обрезанным, испорченным или просто чужим. Разбор такого не должен ронять приложение — только «не понял».
 */
class GarbageInTest {

    private val random = Random(2026)

    /** Набор неприятных строк: пустые, обрезанные, с юникодом, похожие на JSON, но не те. */
    private fun junk(): List<String> {
        val fixed = listOf(
            "", " ", "null", "{}", "[]", "{", "}", "\"", "{\"a\":", "[1,2", "0", "-1", "1e999", "NaN",
            "{\"v\":\"x\"}", "{\"mine\":5}", "{\"theirs\":[{\"s\":\"x\"}]}", "{\"m\":[null]}",
            "\u0000\u0001\u0002", "😀😀😀", "\uD83D", "kds-", "kds-xxxxx", "KE2:", "KE2:AAAA", "KE2:!!!!",
            "<html><table class=\"crud\"><tr><td rowspan=\"3\">Понедельник 99.99</td></tr></table></html>",
            "a".repeat(100_000),
        )
        val noise = (1..200).map {
            val length = random.nextInt(0, 300)
            buildString { repeat(length) { append(random.nextInt(1, 0x2FFF).toChar()) } }
        }
        return fixed + noise
    }

    /** Испортить строку: вырезать кусок, заменить байты, обрезать. */
    private fun mutations(valid: String): List<String> = (1..150).map {
        val chars = valid.toCharArray()
        when (it % 4) {
            0 -> valid.take(random.nextInt(0, valid.length + 1))
            1 -> {
                repeat(random.nextInt(1, 6)) { chars[random.nextInt(chars.size)] = random.nextInt(32, 126).toChar() }
                String(chars)
            }
            2 -> valid.removeRange(random.nextInt(valid.length / 2), random.nextInt(valid.length / 2, valid.length))
            else -> valid.reversed()
        }
    }

    private fun noCrash(what: String, inputs: List<String>, parse: (String) -> Any?) {
        for (input in inputs) {
            try {
                parse(input)
            } catch (e: Throwable) {
                throw AssertionError("$what упал на «${input.take(80)}»: $e", e)
            }
        }
    }

    @Test
    fun chatStateFromDiskNeverCrashes() {
        val good = ChatLogic.encodeState(
            ChatLogic.append(ChatState(restored = true), Person.AIZAT, "привет", 1_000),
        )
        noCrash("переписка с диска", junk() + mutations(good)) { ChatLogic.decodeState(it) }
        noCrash("голова из облака", junk() + mutations(good)) { ChatLogic.decodeHead(it) }
        noCrash("страница из облака", junk() + mutations(good)) { ChatLogic.decodePage(it) }
        noCrash("копия в пуше", junk() + mutations(good)) { ChatLogic.decodePreview(it) }
        assertEquals("привет", ChatLogic.decodeState(good)!!.mine.single().text)
    }

    @Test
    fun sharedEventsAndPairCodesFromMessengerNeverCrash() {
        val good = PartnerShare.encode(SharedEvents(Person.NASTYA, 5L, emptyList(), drops = listOf(1L, 2L)))
        noCrash("дела второго человека", junk() + mutations(good)) { PartnerShare.decode(it) }
        noCrash("код связи", junk() + mutations(PairCode.display(PairCode.generate()))) { PairCode.parse(it) }
        assertEquals(listOf(1L, 2L), PartnerShare.decode("Вот: $good !")!!.drops)
    }

    @Test
    fun diaryAndBackupFilesNeverCrash() {
        noCrash("дневник", junk()) { DiaryCodec.decode(it) }
        noCrash("резервная копия", junk()) { Backup.decode(it) }
        assertNull(Backup.decode("{\"app\":\"чужое\"}"))
    }

    @Test
    fun encryptedDataFromCloudNeverCrashes() {
        val keys = CloudCrypto.derive(PairCode.generate())
        val good = CloudCrypto.seal(keys, "doc-id-1234567890abc", "секрет")
        noCrash("расшифровка", junk() + mutations(good)) { CloudCrypto.open(keys, "doc-id-1234567890abc", it) }
        // Испорченное не расшифровывается в мусор: либо то же самое, либо ничего.
        for (bad in mutations(good)) {
            val opened = CloudCrypto.open(keys, "doc-id-1234567890abc", bad)
            assertTrue("испорченное расшифровалось в «$opened»", opened == null || opened == "секрет")
        }
        assertNull(CloudCrypto.open(keys, "другой-документ-12345", good))
    }

    @Test
    fun schedulePagesNeverCrash() {
        val monday = LocalDate.of(2026, 11, 2)
        noCrash("страница timeo", junk()) { TimeoParser.parse(it, monday) }
        noCrash("подпись дня", junk()) { TimeoParser.parseDayLabel(it, monday) }
        noCrash("клетка таблицы колледжа", junk()) { CollegeParser.parseEntry(it) }
        // Время пары «24:00» или «8:70» не роняет неделю: строка пропускается, остальные пары на месте.
        val html = """
            <table class="crud">
              <tr><td rowspan="3">Понедельник 02.11</td><td>1</td><td>8:30-10:00</td><td>Математика</td><td></td><td>Иванов</td><td>101</td></tr>
              <tr><td>2</td><td>23:30-24:00</td><td>Ночная</td><td></td><td></td><td>102</td></tr>
              <tr><td>3</td><td>8:70-9:10</td><td>Опечатка</td><td></td><td></td><td>103</td></tr>
            </table>
        """.trimIndent()
        assertEquals(listOf("Математика"), TimeoParser.parse(html, monday).map { it.subject })
    }

    @Test
    fun weatherAnswersWithHolesStillParse() {
        val json = """
            {"current":{"time":"2026-11-06T09:00","temperature_2m":null},
             "hourly":{"time":["2026-11-06T08:00","2026-11-06T09:00"],"temperature_2m":[1.5,null],"weather_code":[null]},
             "daily":{"time":["2026-11-06","2026-11-07"],"temperature_2m_max":[5.0],"temperature_2m_min":[1.0,null]}}
        """.trimIndent()
        val forecast = WeatherParser.parseForecast(json)
        assertNull(forecast.current)
        assertEquals(1, forecast.hours.size)
        assertEquals(1, forecast.days.size)
        // Ответ вообще не JSON — ошибка, которую ловят вызывающие (прогноз из памяти остаётся).
        noCrash("погода целиком") { runCatching { WeatherParser.parseForecast(it) } }
    }

    private fun noCrash(what: String, parse: (String) -> Any?) = noCrash(what, junk(), parse)

    @Test
    fun moneyAndReleaseTextsNeverCrash() {
        noCrash("сумма", junk()) { MoneyFormat.parse(it) }
        noCrash("ответ GitHub", junk()) { ReleaseParser.fromApi(it) }
        noCrash("адрес версии", junk()) { ReleaseParser.fromTagUrl(it) }
        assertEquals(150_050L, MoneyFormat.parse("1 500,50 ₽"))
        assertNull(MoneyFormat.parse("-5"))
        assertNull(MoneyFormat.parse("0"))
    }
}
