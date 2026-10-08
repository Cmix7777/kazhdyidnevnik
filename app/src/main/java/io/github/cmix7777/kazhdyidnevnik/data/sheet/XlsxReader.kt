package io.github.cmix7777.kazhdyidnevnik.data.sheet

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Новый формат Excel (.xlsx): zip-архив с XML. Читает первый лист. */
internal object XlsxReader {

    private const val MAX_ENTRY = 30 * 1024 * 1024

    fun read(bytes: ByteArray): Grid {
        val files = unzip(bytes)
        val sheetPath = firstSheetPath(files) ?: "xl/worksheets/sheet1.xml"
        val sheet = files[sheetPath] ?: files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .minOrNull()?.let { files[it] }
            ?: throw SheetFormatException("в таблице нет листа")
        val strings = files["xl/sharedStrings.xml"]?.let { sharedStrings(it) }.orEmpty()

        val cells = HashMap<Pair<Int, Int>, String>()
        val merges = ArrayList<CellRange>()
        val root = parse(sheet)
        var lastRow = -1
        for (row in descendants(root, "row")) {
            val rowNumber = row.getAttribute("r").toIntOrNull()?.minus(1) ?: (lastRow + 1)
            lastRow = rowNumber
            var lastCol = -1
            for (cell in children(row, "c")) {
                val position = cellPosition(cell.getAttribute("r"))
                val col = position?.second ?: (lastCol + 1)
                lastCol = col
                val text = cellText(cell, strings) ?: continue
                cells[(position?.first ?: rowNumber) to col] = text
            }
        }
        for (merge in descendants(root, "mergeCell")) {
            val parts = merge.getAttribute("ref").split(':')
            val from = cellPosition(parts.getOrNull(0)) ?: continue
            val to = cellPosition(parts.getOrNull(1)) ?: from
            merges += CellRange(from.first, to.first, from.second, to.second)
        }
        return Grid(cells, merges)
    }

    private fun cellText(cell: Element, strings: List<String>): String? {
        val value = children(cell, "v").firstOrNull()?.textContent
        return when (cell.getAttribute("t")) {
            "s" -> value?.trim()?.toIntOrNull()?.let { strings.getOrNull(it) }
            "inlineStr" -> children(cell, "is").firstOrNull()?.let { richText(it) }
            "str", "e" -> value?.let { unescape(it) }
            "b" -> value
            else -> value?.trim()?.toDoubleOrNull()?.let { formatNumber(it) } ?: value
        }
    }

    private fun sharedStrings(xml: ByteArray): List<String> =
        children(parse(xml), "si").map { richText(it) }

    /** Текст строки: прямой <t> или куски <r><t>; фонетические подсказки <rPh> пропускаем. */
    private fun richText(element: Element): String = buildString {
        for (child in children(element)) {
            when (localName(child)) {
                "t" -> append(unescape(child.textContent))
                "r" -> children(child, "t").forEach { append(unescape(it.textContent)) }
            }
        }
    }

    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val workbook = files["xl/workbook.xml"]?.let { parse(it) } ?: return null
        val sheet = descendants(workbook, "sheet").firstOrNull() ?: return null
        val relId = attributeByLocalName(sheet, "id") ?: return null
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { parse(it) } ?: return null
        val target = descendants(rels, "Relationship")
            .firstOrNull { it.getAttribute("Id") == relId }
            ?.getAttribute("Target")
            ?: return null
        return if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                if (!entry.isDirectory && (name.endsWith(".xml") || name.endsWith(".rels"))) {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (out.size() > MAX_ENTRY) throw SheetFormatException("таблица слишком большая")
                    }
                    result[name] = out.toByteArray()
                }
            }
        }
        if (result.isEmpty()) throw SheetFormatException("архив не похож на таблицу Excel")
        return result
    }

    private fun parse(xml: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.isExpandEntityReferences = false }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
    }

    private fun localName(node: Node): String = node.localName ?: node.nodeName.substringAfter(':')

    private fun children(element: Element, name: String? = null): List<Element> {
        val result = ArrayList<Element>()
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && (name == null || localName(node) == name)) result += node
        }
        return result
    }

    private fun descendants(element: Element, name: String): List<Element> {
        val result = ArrayList<Element>()
        fun walk(current: Element) {
            for (child in children(current)) {
                if (localName(child) == name) result += child
                walk(child)
            }
        }
        walk(element)
        return result
    }

    private fun attributeByLocalName(element: Element, name: String): String? {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            if (localName(attribute) == name && attribute.nodeName != name) return attribute.nodeValue
        }
        return element.getAttribute(name).takeIf { it.isNotEmpty() }
    }

    /** «K13» -> (12, 10). */
    private fun cellPosition(ref: String?): Pair<Int, Int>? {
        val match = CELL_REF.matchEntire(ref?.trim()?.uppercase() ?: return null) ?: return null
        var col = 0
        for (ch in match.groupValues[1]) col = col * 26 + (ch - 'A' + 1)
        return (match.groupValues[2].toInt() - 1) to (col - 1)
    }

    /** Excel кодирует некоторые символы как «_x000D_». */
    private fun unescape(text: String): String =
        ESCAPE.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }

    private val CELL_REF = Regex("""\$?([A-Z]{1,3})\$?(\d+)""")
    private val ESCAPE = Regex("""_x([0-9A-Fa-f]{4})_""")
}
