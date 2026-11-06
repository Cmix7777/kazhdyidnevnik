package io.github.cmix7777.kazhdyidnevnik.testing

import io.github.cmix7777.kazhdyidnevnik.data.CloudToken
import io.github.cmix7777.kazhdyidnevnik.data.HttpReply
import io.github.cmix7777.kazhdyidnevnik.data.HttpTransport
import io.github.cmix7777.kazhdyidnevnik.data.TokenStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Вход в облако без сети: токен живёт в памяти. */
class MemoryTokens : TokenStore {
    @Volatile
    var token: CloudToken? = null
    override fun load(): CloudToken? = token
    override fun save(token: CloudToken?) {
        this.token = token
    }
}

/**
 * Подставной Firestore, который ведёт себя как настоящий: хранит документы, понимает маски полей
 * и предусловия и проверяет те же правила базы, что стоят в проекте. Общий для обычных тестов
 * и тестов на эмуляторе (там им подменяется облако всего приложения через CloudNet).
 */
class FakeFirestore : HttpTransport {
    val docs: MutableMap<String, JsonObject> = java.util.concurrent.ConcurrentHashMap()
    val requests = mutableListOf<String>()
    val bodies = mutableListOf<String>()

    @Synchronized
    override fun get(url: String, headers: Map<String, String>): HttpReply {
        requests += "GET $url"
        val id = url.substringAfter("/documents/shares/").substringBefore("?")
        if (id.length !in 20..60) return denied()
        val fields = docs[id] ?: return HttpReply(404, """{"error":{"code":404,"status":"NOT_FOUND"}}""")
        val shown = if (url.contains("mask.fieldPaths=updatedAt")) JsonObject(fields.filterKeys { it == "updatedAt" }) else fields
        return HttpReply(200, buildJsonObject {
            put("name", "projects/p/databases/(default)/documents/shares/$id")
            put("fields", shown)
        }.toString())
    }

    @Synchronized
    override fun post(url: String, body: String, contentType: String, headers: Map<String, String>): HttpReply {
        requests += "POST $url"
        if (url.contains("accounts:signUp")) {
            return HttpReply(200, """{"idToken":"ID","refreshToken":"R","expiresIn":"3600"}""")
        }
        if (!url.endsWith(":commit")) return HttpReply(500, "")
        bodies += body
        val staged = docs.toMutableMap()
        for (element in Json.parseToJsonElement(body).jsonObject["writes"]!!.jsonArray) {
            val write = element.jsonObject
            val update = write["update"]!!.jsonObject
            val id = update["name"]!!.jsonPrimitive.content.substringAfter("/documents/shares/")
            if (id.length !in 20..60) return denied()
            val mustExist = write["currentDocument"]?.jsonObject?.get("exists")?.jsonPrimitive?.boolean
            // Как в настоящей базе: без документа запись отметки — это создание без payload, его не пускают правила.
            if (mustExist == true && id !in staged) return denied()
            val incoming = update["fields"]!!.jsonObject
            val mask = write["updateMask"]?.jsonObject?.get("fieldPaths")?.jsonArray?.map { it.jsonPrimitive.content }
            val result = if (mask == null) incoming else applyMask(staged[id] ?: JsonObject(emptyMap()), incoming, mask)
            // Правила базы проекта: ровно два поля, payload — строка короче 300 000 знаков.
            if (result.keys != setOf("payload", "updatedAt")) return denied()
            val payload = result["payload"]!!.jsonObject["stringValue"]?.jsonPrimitive?.content ?: return denied()
            if (payload.length >= 300_000) return denied()
            staged[id] = result
        }
        docs.clear()
        docs.putAll(staged)
        return HttpReply(200, """{"writeResults":[{}]}""")
    }

    private fun applyMask(old: JsonObject, incoming: JsonObject, mask: List<String>): JsonObject {
        val out = old.toMutableMap()
        for (path in mask) {
            val parts = path.split('.')
            if (parts.size == 1) {
                val value = incoming[parts[0]]
                if (value == null) out.remove(parts[0]) else out[parts[0]] = value
            } else {
                val (top, sub) = parts
                val oldMap = runCatching { out[top]!!.jsonObject["mapValue"]!!.jsonObject["fields"]!!.jsonObject }
                    .getOrDefault(JsonObject(emptyMap()))
                val value: JsonElement = incoming[top]!!.jsonObject["mapValue"]!!.jsonObject["fields"]!!.jsonObject[sub]!!
                out[top] = buildJsonObject {
                    put("mapValue", buildJsonObject { put("fields", JsonObject(oldMap + (sub to value))) })
                }
            }
        }
        return JsonObject(out)
    }

    private fun denied() = HttpReply(403, """{"error":{"code":403,"message":"Missing or insufficient permissions."}}""")
}
