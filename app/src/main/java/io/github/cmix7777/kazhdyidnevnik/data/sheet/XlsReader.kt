package io.github.cmix7777.kazhdyidnevnik.data.sheet

/**
 * Старый формат Excel (.xls, BIFF8 внутри составного файла OLE2).
 * Читает только то, что нужно для расписания: текст и числа ячеек первого листа и объединения.
 */
internal object XlsReader {

    fun read(bytes: ByteArray): Grid {
        val file = CompoundFile(bytes)
        val stream = file.stream("Workbook") ?: file.stream("Book")
            ?: throw SheetFormatException("в файле нет книги Excel")
        return Biff8(stream).firstSheet()
    }
}

private fun u8(data: ByteArray, at: Int): Int = data[at].toInt() and 0xFF

private fun u16(data: ByteArray, at: Int): Int = u8(data, at) or (u8(data, at + 1) shl 8)

private fun s32(data: ByteArray, at: Int): Int = u16(data, at) or (u16(data, at + 2) shl 16)

/** Составной файл OLE2: таблица размещения секторов, каталог и потоки. */
private class CompoundFile(private val data: ByteArray) {

    private class Entry(val name: String, val type: Int, val start: Int, val size: Int)

    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val miniFatStart: Int
    private val fat: IntArray
    private val entries: List<Entry>

    init {
        if (data.size < HEADER) throw SheetFormatException("файл Excel обрезан")
        sectorSize = 1 shl u16(data, 0x1E)
        miniSectorSize = 1 shl u16(data, 0x20)
        if (sectorSize !in 128..65536 || miniSectorSize !in 16..sectorSize) {
            throw SheetFormatException("непонятный заголовок файла Excel")
        }
        val dirStart = s32(data, 0x30)
        miniCutoff = s32(data, 0x38)
        miniFatStart = s32(data, 0x3C)

        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val sector = s32(data, 0x4C + i * 4)
            if (sector >= 0) fatSectors += sector
        }
        var difat = s32(data, 0x44)
        var guard = 0
        val perSector = sectorSize / 4
        while (difat >= 0 && guard++ < MAX_CHAIN) {
            val offset = offsetOf(difat)
            if (offset + sectorSize > data.size) break
            for (i in 0 until perSector - 1) {
                val sector = s32(data, offset + i * 4)
                if (sector >= 0) fatSectors += sector
            }
            difat = s32(data, offset + (perSector - 1) * 4)
        }

        fat = IntArray(fatSectors.size * perSector) { FREE }
        fatSectors.forEachIndexed { index, sector ->
            val offset = offsetOf(sector)
            for (i in 0 until perSector) {
                val at = offset + i * 4
                if (at + 4 <= data.size) fat[index * perSector + i] = s32(data, at)
            }
        }

        val directory = readChain(dirStart)
        entries = (0 until directory.size / 128).map { i ->
            val at = i * 128
            val nameBytes = (u16(directory, at + 0x40) - 2).coerceIn(0, 62)
            Entry(
                name = String(directory, at, nameBytes, Charsets.UTF_16LE),
                type = u8(directory, at + 0x42),
                start = s32(directory, at + 0x74),
                size = s32(directory, at + 0x78),
            )
        }
    }

    /** Поток по имени (без учёта регистра) или null. */
    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == STREAM && it.name.equals(name, ignoreCase = true) } ?: return null
        if (entry.size < 0) return null
        val bytes = if (entry.size < miniCutoff) readMini(entry.start, entry.size) else readChain(entry.start)
        return if (bytes.size > entry.size) bytes.copyOf(entry.size) else bytes
    }

    private fun offsetOf(sector: Int): Int = (sector + 1) * sectorSize

    private fun readChain(start: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        var guard = 0
        while (sector >= 0 && sector < fat.size && guard++ < MAX_CHAIN) {
            val offset = offsetOf(sector)
            if (offset >= data.size) break
            out.write(data, offset, minOf(sectorSize, data.size - offset))
            sector = fat[sector]
        }
        return out.toByteArray()
    }

    private fun readMini(start: Int, size: Int): ByteArray {
        val root = entries.firstOrNull { it.type == ROOT } ?: return ByteArray(0)
        val container = readChain(root.start)
        val miniFatBytes = readChain(miniFatStart)
        val miniFat = IntArray(miniFatBytes.size / 4) { s32(miniFatBytes, it * 4) }
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        var guard = 0
        while (sector >= 0 && out.size() < size && guard++ < MAX_CHAIN) {
            val offset = sector * miniSectorSize
            if (offset >= container.size) break
            out.write(container, offset, minOf(miniSectorSize, container.size - offset))
            sector = if (sector < miniFat.size) miniFat[sector] else END
        }
        return out.toByteArray()
    }

    private companion object {
        const val HEADER = 512
        const val FREE = -1
        const val END = -2
        const val STREAM = 2
        const val ROOT = 5
        const val MAX_CHAIN = 1_000_000
    }
}

/** Записи BIFF8: общая часть книги (строки SST, список листов) и сам лист. */
private class Biff8(private val s: ByteArray) {

    private class Record(val type: Int, val start: Int, val length: Int)

    private fun records(from: Int): Sequence<Record> = sequence {
        var pos = from
        while (pos + 4 <= s.size) {
            val type = u16(s, pos)
            val length = u16(s, pos + 2)
            if (pos + 4 + length > s.size) break
            yield(Record(type, pos + 4, length))
            if (type == EOF) break
            pos += 4 + length
        }
    }

    fun firstSheet(): Grid {
        val first = records(0).firstOrNull()
        if (first == null || first.type != BOF || first.length < 2) {
            throw SheetFormatException("в книге Excel нет начала")
        }
        if (u16(s, first.start) != BIFF8) throw SheetFormatException("слишком старая версия Excel")

        var sheetOffset = -1
        val sst = ArrayList<ByteArray>()
        var inSst = false
        for (record in records(0)) {
            when (record.type) {
                BOUNDSHEET -> {
                    val kind = u8(s, record.start + 5)
                    if (sheetOffset < 0 && kind == 0) sheetOffset = s32(s, record.start)
                }
                SST -> {
                    sst.clear()
                    sst += s.copyOfRange(record.start, record.start + record.length)
                }
                CONTINUE -> if (inSst) sst += s.copyOfRange(record.start, record.start + record.length)
            }
            if (record.type != CONTINUE) inSst = record.type == SST
        }
        if (sheetOffset < 0 || sheetOffset >= s.size) throw SheetFormatException("в книге Excel нет листа")
        val strings = if (sst.isEmpty()) emptyList() else parseSst(sst)

        val cells = HashMap<Pair<Int, Int>, String>()
        val merges = ArrayList<CellRange>()
        var pendingFormula: Pair<Int, Int>? = null
        for (record in records(sheetOffset).drop(1)) {
            val at = record.start
            when (record.type) {
                BOF -> break
                LABELSST -> strings.getOrNull(s32(s, at + 6))?.let { cells[u16(s, at) to u16(s, at + 2)] = it }
                LABEL, RSTRING -> cells[u16(s, at) to u16(s, at + 2)] = unicodeString(at + 6, at + record.length)
                NUMBER -> cells[u16(s, at) to u16(s, at + 2)] =
                    formatNumber(java.lang.Double.longBitsToDouble(long(at + 6)))
                RK -> cells[u16(s, at) to u16(s, at + 2)] = formatNumber(rk(s32(s, at + 6)))
                MULRK -> {
                    val row = u16(s, at)
                    val firstCol = u16(s, at + 2)
                    val count = (record.length - 6) / 6
                    for (i in 0 until count) {
                        cells[row to firstCol + i] = formatNumber(rk(s32(s, at + 4 + i * 6 + 2)))
                    }
                }
                FORMULA -> {
                    val row = u16(s, at)
                    val col = u16(s, at + 2)
                    pendingFormula = null
                    if (u16(s, at + 12) == 0xFFFF) {
                        if (u8(s, at + 6) == 0) pendingFormula = row to col
                    } else {
                        cells[row to col] = formatNumber(java.lang.Double.longBitsToDouble(long(at + 6)))
                    }
                }
                STRING -> {
                    pendingFormula?.let { cells[it] = unicodeString(at, at + record.length) }
                    pendingFormula = null
                }
                MERGEDCELLS -> {
                    val count = u16(s, at)
                    for (i in 0 until count) {
                        val p = at + 2 + i * 8
                        if (p + 8 > at + record.length) break
                        merges += CellRange(u16(s, p), u16(s, p + 2), u16(s, p + 4), u16(s, p + 6))
                    }
                }
            }
        }
        return Grid(cells, merges)
    }

    private fun long(at: Int): Long =
        (s32(s, at).toLong() and 0xFFFFFFFFL) or (s32(s, at + 4).toLong() shl 32)

    /** Строка с длиной и флагами внутри одной записи (LABEL, STRING). */
    private fun unicodeString(at: Int, end: Int): String {
        if (at + 3 > end) return ""
        val count = u16(s, at)
        val high = u8(s, at + 2) and 1 != 0
        val sb = StringBuilder(count)
        var p = at + 3
        repeat(count) {
            if (high) {
                if (p + 2 > end) return sb.toString()
                sb.append(u16(s, p).toChar())
                p += 2
            } else {
                if (p + 1 > end) return sb.toString()
                sb.append(u8(s, p).toChar())
                p += 1
            }
        }
        return sb.toString()
    }

    /** Таблица общих строк. Строки могут переходить из записи в запись (CONTINUE). */
    private fun parseSst(segments: List<ByteArray>): List<String> {
        val reader = SegmentReader(segments)
        reader.skip(4)
        val unique = reader.u32()
        val result = ArrayList<String>(unique.coerceIn(0, 100_000))
        repeat(unique.coerceAtLeast(0)) {
            if (!reader.hasMore()) return result
            val count = reader.u16()
            val flags = reader.u8()
            val runs = if (flags and 0x08 != 0) reader.u16() else 0
            val ext = if (flags and 0x04 != 0) reader.u32() else 0
            result += reader.chars(count, flags and 0x01 != 0)
            reader.skip(4L * runs)
            reader.skip(ext.toLong() and 0xFFFFFFFFL)
        }
        return result
    }

    private class SegmentReader(private val segments: List<ByteArray>) {
        private var index = 0
        private var pos = 0

        private fun settle() {
            while (index < segments.size && pos >= segments[index].size) {
                index++
                pos = 0
            }
        }

        fun hasMore(): Boolean {
            settle()
            return index < segments.size
        }

        fun u8(): Int {
            settle()
            if (index >= segments.size) return 0
            return segments[index][pos++].toInt() and 0xFF
        }

        fun u16(): Int = u8() or (u8() shl 8)

        fun u32(): Int = u16() or (u16() shl 16)

        fun skip(count: Long) {
            var left = count
            while (left > 0) {
                settle()
                if (index >= segments.size) return
                val take = minOf(left, (segments[index].size - pos).toLong()).toInt()
                pos += take
                left -= take
            }
        }

        /** Символы строки. На границе записи продолжение начинается с нового байта флагов. */
        fun chars(count: Int, highAtStart: Boolean): String {
            val sb = StringBuilder(count)
            var high = highAtStart
            var left = count
            while (left > 0) {
                if (index >= segments.size) break
                val segment = segments[index]
                val need = if (high) 2 else 1
                if (pos + need > segment.size) {
                    index++
                    pos = 0
                    if (index >= segments.size) break
                    high = segments[index][pos++].toInt() and 0x01 != 0
                    continue
                }
                val code = if (high) {
                    (segment[pos].toInt() and 0xFF) or ((segment[pos + 1].toInt() and 0xFF) shl 8)
                } else {
                    segment[pos].toInt() and 0xFF
                }
                pos += need
                sb.append(code.toChar())
                left--
            }
            return sb.toString()
        }
    }

    private companion object {
        const val BOF = 0x0809
        const val EOF = 0x000A
        const val BIFF8 = 0x0600
        const val BOUNDSHEET = 0x0085
        const val SST = 0x00FC
        const val CONTINUE = 0x003C
        const val LABELSST = 0x00FD
        const val LABEL = 0x0204
        const val RSTRING = 0x00D6
        const val NUMBER = 0x0203
        const val RK = 0x027E
        const val MULRK = 0x00BD
        const val FORMULA = 0x0006
        const val STRING = 0x0207
        const val MERGEDCELLS = 0x00E5

        fun rk(raw: Int): Double {
            var value = if (raw and 0x02 != 0) {
                (raw shr 2).toDouble()
            } else {
                java.lang.Double.longBitsToDouble((raw.toLong() and 0xFFFFFFFCL) shl 32)
            }
            if (raw and 0x01 != 0) value /= 100
            return value
        }
    }
}

/** Число из ячейки как текст: целые без «.0». */
internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
