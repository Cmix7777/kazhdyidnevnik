package io.github.cmix7777.kazhdyidnevnik.service

import io.github.cmix7777.kazhdyidnevnik.data.HttpReply
import io.github.cmix7777.kazhdyidnevnik.data.HttpTransport
import org.jsoup.Connection
import org.jsoup.Jsoup

/** Запросы к облаку через Jsoup: он уже есть в приложении, отдельная сетевая библиотека не нужна. */
object JsoupTransport : HttpTransport {

    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "Kazhdyidnevnik-Android"

    override fun get(url: String, headers: Map<String, String>): HttpReply =
        run(Jsoup.connect(url).method(Connection.Method.GET), headers)

    override fun post(url: String, body: String, contentType: String, headers: Map<String, String>): HttpReply =
        run(
            Jsoup.connect(url).method(Connection.Method.POST).header("Content-Type", contentType).requestBody(body),
            headers,
        )

    private fun run(connection: Connection, headers: Map<String, String>): HttpReply {
        headers.forEach { (name, value) -> connection.header(name, value) }
        val response = connection
            .userAgent(USER_AGENT)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .timeout(TIMEOUT_MS)
            .execute()
        return HttpReply(response.statusCode(), response.body())
    }
}
