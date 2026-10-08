package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.data.sheet.CellRange
import io.github.cmix7777.kazhdyidnevnik.data.sheet.Grid
import io.github.cmix7777.kazhdyidnevnik.data.sheet.SheetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

class CollegeParserTest {

    private fun bytes(name: String) = requireNotNull(javaClass.getResource("/college/$name")).readBytes()

    private val xls = SheetReader.read(bytes("2-smena-2026-10-08.xls"))
    private val monday = LocalDate.of(2026, 10, 12)

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun readsOldExcelFile() {
        assertEquals("ТОРГОВОЕ ДЕЛО\nКУРС 3/9", xls.text(5, 10))
        assertEquals("8.10-9.30", xls.text(8, 1))
        assertEquals(249, xls.merges.size)
        assertEquals(CellRange(10, 11, 1, 1), xls.mergeAt(11, 1))
        // Ячейка внутри объединения показывает текст левой верхней.
        assertEquals("12.00-13.30", xls.value(11, 1))
        assertNull(xls.text(11, 1))
        assertEquals("Е.Ф. Тенсина", xls.text(74, 6))
    }

    @Test
    fun readsNewExcelFileTheSameWay() {
        val xlsx = SheetReader.read(bytes("2-smena-copy.xlsx"))
        assertEquals(xls.merges.toSet(), xlsx.merges.toSet())
        for (row in 0..xls.lastRow) {
            assertEquals(xls.text(row, 10), xlsx.text(row, 10))
            assertEquals(xls.text(row, 1), xlsx.text(row, 1))
        }
        val fromXls = CollegeParser.parse(xls).week(monday)
        val fromXlsx = CollegeParser.parse(xlsx).week(monday)
        assertEquals(fromXls, fromXlsx)
    }

    @Test
    fun nastyaWeek() {
        val table = CollegeParser.parse(xls)
        assertEquals("ТОРГОВОЕ ДЕЛО КУРС 3/9", table.group)
        val week = table.week(monday)
        assertEquals(17, week.size)
        assertEquals(
            listOf(2, 4, 4, 3, 2, 2),
            (0..5).map { day -> week.count { it.date == monday.plusDays(day.toLong()) } },
        )
        assertTrue(week.none { it.date.dayOfWeek == DayOfWeek.SUNDAY })

        val first = week.first()
        assertEquals(monday, first.date)
        assertEquals(t(13, 40), first.start)
        assertEquals(t(15, 10), first.end)
        assertEquals(4, first.number)
        assertEquals("Безопасность жизнедеятельности", first.subject)
        assertEquals("Иванов А.А.", first.teacher)
        assertEquals("200а/4", first.room)
        assertEquals("", first.note)
    }

    @Test
    fun tuesdayDetails() {
        val tuesday = CollegeParser.parse(xls).week(monday).filter { it.date == monday.plusDays(1) }
        val sport = tuesday.first()
        assertEquals(t(12, 0), sport.start)
        assertEquals(3, sport.number)
        assertEquals("Физическая культура", sport.subject)
        assertEquals("Скоробогатов А.В.", sport.teacher)
        assertEquals("корпус 5, тренажерный зал", sport.room)

        val automation = tuesday[1]
        assertEquals("Автоматизация торгово-технологических процессов", automation.subject)
        assertEquals("Мальцева К.А.", automation.teacher)
        assertEquals("105/3", automation.room)
        assertEquals("возможны изменения в расписании", automation.note)
        assertEquals(listOf("Логистика", "Логистика"), tuesday.drop(2).map { it.subject })
        assertEquals(listOf(t(15, 30), t(17, 10)), tuesday.drop(2).map { it.start })
    }

    @Test
    fun earlyEndAndRoomNotes() {
        val week = CollegeParser.parse(xls).week(monday)
        val finance = week.first { it.date == monday.plusDays(2) }
        assertEquals("Финансы, налоги и налогообложение", finance.subject)
        assertEquals(t(15, 0), finance.end)
        assertEquals("202/6", finance.room)
        assertEquals("до 15.00 (возможны изменения в расписании)", finance.note)

        val sales = week.first { it.date == monday.plusDays(3) }
        assertEquals("Технология продаж потребительских товаров и координация работы с клиентами", sales.subject)
        assertEquals("Кузьмин Е.В.", sales.teacher)
        assertEquals("011/2", sales.room)
        assertEquals("по знам, по числ 202/3", sales.note)

        val saturday = week.filter { it.date == monday.plusDays(5) }
        assertEquals("Организация торгово-сбытовой деятельности на внутреннем и внешнем рынках", saturday[0].subject)
        assertEquals("Кузьмина А.В.", saturday[0].teacher)
        assertEquals("115а/4", saturday[0].room)
        assertEquals("", saturday[0].note)
    }

    @Test
    fun datesInCells() {
        val except = CollegeParser.parseEntry("6.10 не будет Огневая подготовка ШейдаевТ.Т.123/4")!!
        assertEquals(listOf(MonthDay.of(10, 6)), except.exceptOn)
        assertEquals("Огневая подготовка", except.subject)
        assertEquals("Шейдаев Т.Т.", except.teacher)
        assertEquals("123/4", except.room)

        val only = CollegeParser.parseEntry("31.10., 7.11Экологическое право Коршунова Е.А.115/4")!!
        assertEquals(listOf(MonthDay.of(10, 31), MonthDay.of(11, 7)), only.onlyOn)
        assertEquals("Экологическое право", only.subject)

        val year = CollegeParser.parseEntry("26.10.2026 Жилищное право (прак) Кондаков А.С.24/1")!!
        assertEquals(listOf(MonthDay.of(10, 26)), year.onlyOn)
        assertEquals("Жилищное право (прак)", year.subject)

        val from = CollegeParser.parseEntry("с 10.10 в 11.30 Физическая культура Баженова М.В.")!!
        assertEquals(MonthDay.of(10, 10), from.from)
        assertEquals(t(11, 30), from.startAt)
        assertEquals("Физическая культура", from.subject)
        assertEquals("Баженова М.В.", from.teacher)

        val roomFirst = CollegeParser.parseEntry("6.10 229/1 Иностранный язык Айнулина Н.В.")!!
        assertEquals("229/1", roomFirst.room)
        assertEquals("Иностранный язык", roomFirst.subject)

        assertNull(CollegeParser.parseEntry(" "))
        assertNull(CollegeParser.parseEntry("12"))
    }

    @Test
    fun alternativesAndOneOffChanges() {
        val cells = mapOf(
            (0 to 2) to "ТОРГОВОЕ ДЕЛО",
            (1 to 0) to "Понедельник",
            (1 to 1) to "8.10-9.40",
            (1 to 2) to "Математика Петрова И.И.101/1",
            (2 to 2) to "Химия Сидорова А.А.102/2",
            (3 to 1) to "9.50-11.20",
            (3 to 2) to "Статистика Орлова О.О.103/3",
            (4 to 2) to "19.10 Право Иванов И.И.104/4",
            (5 to 0) to "Вторник",
            (5 to 1) to "8.10-9.40",
            (5 to 2) to "с 20.10 Физкультура Петров П.П.5 корп спортзал",
            (6 to 1) to "8.10-9.40",
            (6 to 2) to "Экономика Смирнова С.С.105/5",
        )
        val merges = listOf(CellRange(1, 2, 1, 1), CellRange(3, 4, 1, 1))
        val table = CollegeParser.parse(Grid(cells, merges))

        val first = table.week(monday)
        val mondayLessons = first.filter { it.date == monday }
        assertEquals(listOf("Математика", "Химия", "Статистика"), mondayLessons.map { it.subject })
        assertEquals(listOf("по числителю", "по знаменателю", ""), mondayLessons.map { it.note })
        assertTrue(first.none { it.date == monday.plusDays(1) })
        // Строка без подписи дня, но время снова утреннее — это уже среда.
        assertEquals("Экономика", first.single { it.date == monday.plusDays(2) }.subject)

        val second = table.week(monday.plusWeeks(1))
        assertEquals(
            listOf("Математика", "Химия", "Право"),
            second.filter { it.date == monday.plusWeeks(1) }.map { it.subject },
        )
        val sport = second.single { it.date == monday.plusWeeks(1).plusDays(1) }
        assertEquals("Физкультура", sport.subject)
        assertEquals("корпус 5, спортзал", sport.room)
    }

    @Test
    fun findsFileLink() {
        val links = listOf(
            "1 Смена Юр, ПД, Экон, Торг.Д" to "/files/assets/013805-1%20Смена%20Юр,%20ПД,%20Экон,%20Торг.Д.xls",
            "2 смена РНГ, СА, ПБ" to "/files/assets/013807-2%20смена%20РНГ,%20СА,%20ПБ.xlsx",
            "2 смена ЮР, ПД, ТД" to "/files/assets/013808-2%20смена%20ЮР,%20ПД,%20ТД.xls",
            "Юриспруденция заочка 3 курс" to "/files/assets/013812-Юриспруденция.pdf",
        )
        val link = CollegeLinks.find(links)!!
        assertEquals("/files/assets/013808-2%20смена%20ЮР,%20ПД,%20ТД.xls", link)
        assertEquals(CollegeLinks.DEFAULT_FILE, CollegeLinks.normalize(link))
        assertNull(CollegeLinks.find(links.take(2)))
    }
}
