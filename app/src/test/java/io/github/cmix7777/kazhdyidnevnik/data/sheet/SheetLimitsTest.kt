package io.github.cmix7777.kazhdyidnevnik.data.sheet

import io.github.cmix7777.kazhdyidnevnik.data.CollegeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Таблица колледжа может прийти странной: такие файлы читаются быстро или честно не читаются, но не виснут. */
class SheetLimitsTest {

    private fun bytes(name: String) = requireNotNull(javaClass.getResource("/college/$name")).readBytes()

    @Test(timeout = 5_000)
    fun mergeOverWholeSheetIsCheap() {
        val whole = CellRange(0, 1_048_575, 0, 16_383)
        val grid = Grid(mapOf((0 to 0) to "a", (5 to 3) to "b"), listOf(whole))
        assertEquals(whole, grid.mergeAt(5, 3))
        assertEquals("a", grid.value(5, 3))
        // За пределами заполненной части объединение тоже находится.
        assertEquals(whole, grid.mergeAt(900_000, 2))
        assertNull(Grid(emptyMap(), listOf(whole)).text(0, 0))
    }

    @Test(timeout = 5_000)
    fun manyBigMergesFallBackToSearch() {
        val merges = (0 until 40).map { CellRange(it * 10, it * 10 + 9, 0, 9_999) }
        val cells = (0 until 400).associate { (it to 9_999) to "x$it" }
        val grid = Grid(cells, merges)
        assertEquals(merges[3], grid.mergeAt(35, 500))
        assertEquals("x30", grid.text(30, 9_999))
        assertNull(grid.value(35, 500))
        assertNull(grid.mergeAt(5_000, 0))
    }

    @Test(timeout = 10_000)
    fun realTimetableWithWholeColumnMergeParsesTheSame() {
        val xls = SheetReader.read(bytes("2-smena-2026-10-08.xls"))
        val cells = HashMap<Pair<Int, Int>, String>()
        for (row in 0..xls.lastRow) for (col in 0..xls.lastCol) xls.text(row, col)?.let { cells[row to col] = it }
        // Кто-то в колледже объединил пустой столбец справа до самого низа листа.
        val spoiled = Grid(cells, xls.merges + CellRange(0, 65_535, xls.lastCol + 1, xls.lastCol + 3))
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(CollegeParser.parse(xls).week(monday), CollegeParser.parse(spoiled).week(monday))
    }

    @Test
    fun brokenFilesFailWithReadableErrors() {
        val samples = listOf(
            ByteArray(0),
            "просто текст".toByteArray(),
            byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()),
            byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 0, 0),
            bytes("2-smena-2026-10-08.xls").copyOf(3_000),
            bytes("2-smena-copy.xlsx").copyOf(2_000),
        )
        for (sample in samples) {
            val error = runCatching { CollegeParser.parse(SheetReader.read(sample)) }.exceptionOrNull()
            assertTrue("для ${sample.size} байт: $error", error is SheetFormatException)
        }
    }
}
