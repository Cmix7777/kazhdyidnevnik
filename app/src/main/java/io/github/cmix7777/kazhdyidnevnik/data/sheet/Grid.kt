package io.github.cmix7777.kazhdyidnevnik.data.sheet

/** Объединённые ячейки: номера строк и столбцов с нуля, границы включительно. */
data class CellRange(val firstRow: Int, val lastRow: Int, val firstCol: Int, val lastCol: Int) {
    fun contains(row: Int, col: Int): Boolean = row in firstRow..lastRow && col in firstCol..lastCol
}

/** Таблица с одного листа: текст ячеек и объединения. */
class Grid(cells: Map<Pair<Int, Int>, String>, val merges: List<CellRange>) {

    private val cells: Map<Long, String> = cells.mapKeys { (key, _) -> key(key.first, key.second) }

    /** Номер последней строки, в которой что-то есть (или -1). */
    val lastRow: Int = cells.keys.maxOfOrNull { it.first } ?: -1

    /** Номер последнего столбца, в котором что-то есть (или -1). */
    val lastCol: Int = cells.keys.maxOfOrNull { it.second } ?: -1

    /**
     * Объединения по клеткам — только в пределах заполненной части. Объединение «на весь столбец»
     * (до миллиона строк) иначе раздувало бы указатель до миллионов клеток: телефон считал бы минутами,
     * а капля обновления так и крутилась бы. Если клеток всё равно слишком много, ищем перебором.
     */
    private val mergeIndex: Map<Long, CellRange>? = run {
        val index = HashMap<Long, CellRange>()
        var total = 0L
        for (range in merges) {
            val rows = maxOf(0, range.firstRow)..minOf(lastRow, range.lastRow)
            val cols = maxOf(0, range.firstCol)..minOf(lastCol, range.lastCol)
            if (rows.isEmpty() || cols.isEmpty()) continue
            total += (rows.last - rows.first + 1).toLong() * (cols.last - cols.first + 1)
            if (total > MAX_INDEXED) return@run null
            for (row in rows) {
                for (col in cols) index[key(row, col)] = range
            }
        }
        index
    }

    /** Текст ровно этой ячейки. */
    fun text(row: Int, col: Int): String? = cells[key(row, col)]

    /** Объединение, в которое входит ячейка. */
    fun mergeAt(row: Int, col: Int): CellRange? {
        val index = mergeIndex ?: return merges.firstOrNull { it.contains(row, col) }
        if (row in 0..lastRow && col in 0..lastCol) return index[key(row, col)]
        return merges.firstOrNull { it.contains(row, col) }
    }

    /** Что видно в ячейке: для объединённых — текст левой верхней. */
    fun value(row: Int, col: Int): String? {
        val range = mergeAt(row, col) ?: return text(row, col)
        return text(range.firstRow, range.firstCol)
    }

    private companion object {
        const val MAX_INDEXED = 2_000_000L

        fun key(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)
    }
}

/** Файл расписания не удалось прочитать как таблицу. */
class SheetFormatException(message: String) : Exception(message)

/** Читает первый лист таблицы Excel: старый .xls или новый .xlsx. */
object SheetReader {

    fun read(bytes: ByteArray): Grid = try {
        when {
            isXls(bytes) -> XlsReader.read(bytes)
            isZip(bytes) -> XlsxReader.read(bytes)
            else -> throw SheetFormatException("файл расписания не похож на таблицу Excel")
        }
    } catch (e: SheetFormatException) {
        throw e
    } catch (e: Exception) {
        // Обрезанный или испорченный файл: понятная ошибка вместо падения где-то в разборе.
        throw SheetFormatException("файл расписания повреждён (${e.javaClass.simpleName})")
    }

    private fun isXls(bytes: ByteArray): Boolean =
        bytes.size >= 8 && XLS_MAGIC.indices.all { bytes[it] == XLS_MAGIC[it].toByte() }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private val XLS_MAGIC = intArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
}
