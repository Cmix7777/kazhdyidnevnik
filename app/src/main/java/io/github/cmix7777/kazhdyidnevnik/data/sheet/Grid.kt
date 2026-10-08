package io.github.cmix7777.kazhdyidnevnik.data.sheet

/** Объединённые ячейки: номера строк и столбцов с нуля, границы включительно. */
data class CellRange(val firstRow: Int, val lastRow: Int, val firstCol: Int, val lastCol: Int) {
    fun contains(row: Int, col: Int): Boolean = row in firstRow..lastRow && col in firstCol..lastCol
}

/** Таблица с одного листа: текст ячеек и объединения. */
class Grid(cells: Map<Pair<Int, Int>, String>, val merges: List<CellRange>) {

    private val cells: Map<Long, String> = cells.mapKeys { (key, _) -> key(key.first, key.second) }
    private val mergeIndex: Map<Long, CellRange> = buildMap {
        for (range in merges) {
            for (row in range.firstRow..range.lastRow) {
                for (col in range.firstCol..range.lastCol) put(key(row, col), range)
            }
        }
    }

    /** Номер последней строки, в которой что-то есть (или -1). */
    val lastRow: Int = cells.keys.maxOfOrNull { it.first } ?: -1

    /** Номер последнего столбца, в котором что-то есть (или -1). */
    val lastCol: Int = cells.keys.maxOfOrNull { it.second } ?: -1

    /** Текст ровно этой ячейки. */
    fun text(row: Int, col: Int): String? = cells[key(row, col)]

    /** Объединение, в которое входит ячейка. */
    fun mergeAt(row: Int, col: Int): CellRange? = mergeIndex[key(row, col)]

    /** Что видно в ячейке: для объединённых — текст левой верхней. */
    fun value(row: Int, col: Int): String? {
        val range = mergeAt(row, col) ?: return text(row, col)
        return text(range.firstRow, range.firstCol)
    }

    private companion object {
        fun key(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)
    }
}

/** Файл расписания не удалось прочитать как таблицу. */
class SheetFormatException(message: String) : Exception(message)

/** Читает первый лист таблицы Excel: старый .xls или новый .xlsx. */
object SheetReader {

    fun read(bytes: ByteArray): Grid = when {
        isXls(bytes) -> XlsReader.read(bytes)
        isZip(bytes) -> XlsxReader.read(bytes)
        else -> throw SheetFormatException("файл расписания не похож на таблицу Excel")
    }

    private fun isXls(bytes: ByteArray): Boolean =
        bytes.size >= 8 && XLS_MAGIC.indices.all { bytes[it] == XLS_MAGIC[it].toByte() }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private val XLS_MAGIC = intArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
}
