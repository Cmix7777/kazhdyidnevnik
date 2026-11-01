package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

/**
 * Переписка через облако на подставном Firestore, который ведёт себя как настоящий: хранит документы,
 * понимает маски полей и предусловия и проверяет те же правила базы, что стоят в проекте.
 */
class ChatCloudTest {

    private class Tokens : TokenStore {
        var token: CloudToken? = null
        override fun load(): CloudToken? = token
        override fun save(token: CloudToken?) {
            this.token = token
        }
    }

    private class FakeFirestore : HttpTransport {
        val docs = mutableMapOf<String, JsonObject>()
        val requests = mutableListOf<String>()
        val bodies = mutableListOf<String>()

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

    private val code = "abcdefghjkmnpqrstuvw"
    private val store = FakeFirestore()
    private val aizat = ChatCloud(store, Tokens(), now = { 1_000L })
    private val nastya = ChatCloud(store, Tokens(), now = { 1_000L })

    private fun send(state: ChatState, text: String, version: Long): ChatState {
        val next = ChatLogic.append(state, Person.AIZAT, text, version)
        val plan = ChatLogic.planUpload(next, Person.AIZAT, push = null, hook = null)
        aizat.upload(code, Person.AIZAT, plan.pages, plan.head, version)
        return ChatLogic.afterUpload(next, plan, version, next.revision)
    }

    @Test
    fun messageTravelsEncryptedAndThePollIsTiny() {
        assertNull(nastya.mark(code, Person.AIZAT))
        send(ChatState(restored = true), "Привет, это секрет", 500)

        val mark = nastya.mark(code, Person.AIZAT)!!
        assertEquals(500L, mark.version)
        assertTrue(store.requests.last().endsWith("?mask.fieldPaths=updatedAt"))

        val (head, headMark) = nastya.head(code, Person.AIZAT)!!
        assertEquals("Привет, это секрет", head.messages.single().text)
        assertEquals(500L, headMark.version)
        // В облаке ни текста, ни кода связи.
        val stored = store.docs.values.joinToString { it.toString() }
        assertFalse(stored.contains("секрет"))
        assertFalse(stored.contains(code))
        assertTrue(store.docs.keys.all { it.length in 20..60 })
    }

    @Test
    fun readAndTypingMarksNeedTheHeadAndSurviveNewMessages() {
        assertFalse(aizat.putRead(code, Person.AIZAT, 3))
        assertFalse(aizat.putTyping(code, Person.AIZAT, 123))
        val state = send(ChatState(restored = true), "раз", 10)
        assertTrue(aizat.putRead(code, Person.AIZAT, 3))
        assertTrue(aizat.putTyping(code, Person.AIZAT, 123))
        assertEquals(ChatMark(10, 3, 123), nastya.mark(code, Person.AIZAT))
        // Новое сообщение меняет версию, но не стирает «прочитано» и «печатает».
        send(state, "два", 20)
        assertEquals(ChatMark(20, 3, 123), nastya.mark(code, Person.AIZAT))
        assertTrue(aizat.putTyping(code, Person.AIZAT, 0))
        assertEquals(0L, nastya.mark(code, Person.AIZAT)!!.typing)
        val write = Json.parseToJsonElement(store.bodies.last()).jsonObject["writes"]!!.jsonArray.single().jsonObject
        assertEquals("[\"updatedAt.t\"]", write["updateMask"]!!.jsonObject["fieldPaths"].toString())
    }

    @Test
    fun archivePagesAreWrittenTogetherWithTheHead() {
        var state = ChatState(restored = true)
        repeat(ChatLogic.HEAD_LIMIT_COUNT) { state = ChatLogic.append(state, Person.AIZAT, "сообщение $it", it.toLong()) }
        state = send(state, "последнее", 5_000)
        val writes = Json.parseToJsonElement(store.bodies.last()).jsonObject["writes"]!!.jsonArray
        assertEquals(2, writes.size)

        val (head, _) = nastya.head(code, Person.AIZAT)!!
        assertEquals(1, head.pages)
        assertEquals(1, head.messages.size)
        val page = nastya.page(code, Person.AIZAT, 0)!!
        assertEquals(ChatLogic.HEAD_LIMIT_COUNT, page.messages.size)
        assertNull(nastya.page(code, Person.AIZAT, 1))

        // Второй телефон узнаёт о пропуске и дочитывает архив.
        val merge = ChatLogic.mergeTheirs(ChatState(), head, 1)
        assertEquals(1L, merge.missingFrom)
        val full = ChatLogic.mergeTheirsPage(merge.state, page)
        assertEquals(ChatLogic.HEAD_LIMIT_COUNT + 1, full.theirs.size)
    }

    @Test
    fun photoRoundTripAndWipe() {
        val bytes = ByteArray(150_000).also { Random(7).nextBytes(it) }
        aizat.putFile(code, "0123456789abcdef", bytes, 1)
        assertArrayEquals(bytes, nastya.file(code, "0123456789abcdef"))
        aizat.wipeFile(code, "0123456789abcdef", 2)
        assertNull(nastya.file(code, "0123456789abcdef"))
        assertNull(nastya.file(code, "fedcba9876543210"))
        // Слишком большое фото не уходит: правила его всё равно не пустят.
        try {
            aizat.putFile(code, "0123456789abcdee", ByteArray(ChatCloud.MAX_PAYLOAD), 3)
            fail("большое фото не должно уходить")
        } catch (e: CloudException) {
            assertTrue(e.message!!.contains("большое"))
        }
    }

    @Test
    fun otherCodeSeesNothingAndBrokenHeadIsReported() {
        send(ChatState(restored = true), "только нам", 1)
        val stranger = ChatCloud(store, Tokens())
        assertNull(stranger.head("zyxwvutsrqpnmkjhgfed", Person.AIZAT))

        val docId = CloudCrypto.derive(code).chatHead(Person.AIZAT)
        val broken = JsonObject(store.docs[docId]!! + ("payload" to buildJsonObject { put("stringValue", "KE2:AAAA") }))
        store.docs[docId] = broken
        try {
            nastya.head(code, Person.AIZAT)
            fail("испорченная голова должна быть ошибкой")
        } catch (e: CloudException) {
            assertTrue(e.message!!.contains("не расшифровались"))
            assertEquals(200, e.code)
        }
    }

    @Test
    fun headCannotBePassedOffAsTheOtherPerson() {
        send(ChatState(restored = true), "моё", 1)
        val keys = CloudCrypto.derive(code)
        // Кто-то переложил голову Айзата на место головы Насти: она не расшифруется (имя входит в проверку).
        store.docs[keys.chatHead(Person.NASTYA)] = store.docs[keys.chatHead(Person.AIZAT)]!!
        try {
            aizat.head(code, Person.NASTYA)
            fail("чужая голова не должна читаться")
        } catch (e: CloudException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun pushPreviewOpensOnlyForTheRightSender() {
        val preview = ChatPreview(seq = 5, at = 99, text = "привет")
        val sealed = ChatPreviewSeal.seal(code, Person.AIZAT, preview)
        assertFalse(sealed.contains("привет"))
        assertEquals(preview, ChatPreviewSeal.open(code, Person.AIZAT, sealed))
        assertNull(ChatPreviewSeal.open(code, Person.NASTYA, sealed))
        assertNull(ChatPreviewSeal.open("zyxwvutsrqpnmkjhgfed", Person.AIZAT, sealed))
        assertNull(ChatPreviewSeal.open(code, Person.AIZAT, "мусор"))
    }

    @Test
    fun bytesSealAndOpen() {
        val keys = CloudCrypto.derive(code)
        val data = byteArrayOf(0, 1, 2, -1, 127)
        val sealed = CloudCrypto.sealBytes(keys, "doc-a", data)
        assertArrayEquals(data, CloudCrypto.openBytes(keys, "doc-a", sealed))
        assertNull(CloudCrypto.openBytes(keys, "doc-b", sealed))
        assertNull(CloudCrypto.openBytes(keys, "doc-a", "KE2:short"))
    }
}
