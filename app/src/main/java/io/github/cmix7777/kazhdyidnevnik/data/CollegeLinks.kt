package io.github.cmix7777.kazhdyidnevnik.data

import java.net.URI
import java.net.URL
import java.net.URLDecoder

/**
 * Где лежит расписание Насти. На странице колледжа файлы меняются (номер в начале имени
 * растёт при каждом обновлении), поэтому ищем ссылку по подписи «2 смена ЮР, ПД, ТД».
 */
object CollegeLinks {

    const val PAGE = "https://f-mkpo.udsu.ru/class_times"

    /** Последняя известная ссылка: на случай, если страницу поменяют. */
    const val DEFAULT_FILE =
        "https://f-mkpo.udsu.ru/files/assets/013808-2%20%D1%81%D0%BC%D0%B5%D0%BD%D0%B0%20" +
            "%D0%AE%D0%A0,%20%D0%9F%D0%94,%20%D0%A2%D0%94.xls"

    /** Ссылка на таблицу среди ссылок страницы: пары (подпись, адрес). */
    fun find(links: List<Pair<String, String>>): String? {
        val sheets = links.filter { (_, href) -> isSheet(decode(href)) }
        return sheets.firstOrNull { (text, _) -> matches(text) }?.second
            ?: sheets.firstOrNull { (_, href) -> matches(decode(href).substringAfterLast('/')) }?.second
    }

    /** Адрес, который можно открыть: пробелы и кириллица закодированы, уже закодированное не портится. */
    fun normalize(url: String, base: String = PAGE): String {
        val absolute = URL(URL(base), decode(url))
        return URI(absolute.protocol, absolute.userInfo, absolute.host, absolute.port, absolute.path, absolute.query, null)
            .toASCIIString()
    }

    private fun matches(text: String): Boolean {
        val upper = text.uppercase()
        val words = upper.split(NOT_WORD)
        return upper.contains("СМЕН") && "ТД" in words
    }

    private fun isSheet(href: String): Boolean {
        val path = href.substringBefore('?').lowercase()
        return path.endsWith(".xls") || path.endsWith(".xlsx")
    }

    private fun decode(text: String): String =
        runCatching { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)

    private val NOT_WORD = Regex("""[^А-ЯЁA-Z0-9]+""")
}
