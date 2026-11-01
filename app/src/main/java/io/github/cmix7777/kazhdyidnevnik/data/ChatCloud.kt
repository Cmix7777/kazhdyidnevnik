package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Переписка через тот же бесплатный Firestore, что и обмен делами, в рамках тех же правил базы:
 * документы shares/<имя из кода>-c-<кто> (голова), ...-c-<кто>-<n> (архив) и ...-f-<номер> (фото),
 * у каждого только поля payload и updatedAt. Всё содержимое шифруется на телефоне ключом из кода связи.
 *
 * У головы поле updatedAt — набор из трёх чисел: v (версия, меняется с каждым новым сообщением),
 * r (прочитано до) и t (печатает). Второй телефон опрашивает только его: ответ в сотню байт,
 * а саму голову скачивает, лишь когда версия сменилась.
 */
class ChatCloud(
    http: HttpTransport,
    tokens: TokenStore,
    now: () -> Long = { System.currentTimeMillis() },
    projectId: String = CloudConfig.PROJECT_ID,
    apiKey: String = CloudConfig.API_KEY,
) {

    private val session = FirestoreSession(http, tokens, now, projectId, apiKey)
    private val json = session.json

    /** Отметки головы [person]. null — переписки у него ещё нет. */
    fun mark(code: String, person: Person): ChatMark? {
        val keys = keys(code)
        val reply = session.getShare(keys.chatHead(person), listOf("updatedAt"))
        if (reply.code == 404) return null
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось проверить сообщения")
        return markOf(fields(reply))
    }

    /** Голова [person] целиком. null — переписки у него ещё нет. */
    fun head(code: String, person: Person): Pair<ChatHead, ChatMark>? {
        val keys = keys(code)
        val docId = keys.chatHead(person)
        val reply = session.getShare(docId)
        if (reply.code == 404) return null
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось получить сообщения")
        val fields = fields(reply)
        val head = openPayload(keys, docId, fields)?.let(ChatLogic::decodeHead)
            ?: throw CloudException("Сообщения не расшифровались: у телефонов разные коды связи", reply.code)
        if (head.from != person.id) throw CloudException("В облаке чужая переписка", reply.code)
        return head to markOf(fields)
    }

    /** Архивная страница [index] переписки [person]. null — такой нет. */
    fun page(code: String, person: Person, index: Int): ChatPage? {
        val keys = keys(code)
        val docId = keys.chatPage(person, index)
        val reply = session.getShare(docId)
        if (reply.code == 404) return null
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось получить старые сообщения")
        val page = openPayload(keys, docId, fields(reply))?.let(ChatLogic::decodePage)
            ?: throw CloudException("Старые сообщения не расшифровались", reply.code)
        if (page.from != person.id || page.index != index) throw CloudException("В облаке чужая страница", reply.code)
        return page
    }

    /** Записать страницы и голову [me] разом. Отметки «прочитано» и «печатает» при этом не трогаются. */
    fun upload(code: String, me: Person, pages: List<ChatPage>, head: ChatHead, version: Long) {
        val keys = keys(code)
        val body = buildJsonObject {
            putJsonArray("writes") {
                pages.forEach { page ->
                    val docId = keys.chatPage(me, page.index)
                    add(plainWrite(docId, CloudCrypto.seal(keys, docId, ChatLogic.encodePage(page)), version))
                }
                val docId = keys.chatHead(me)
                add(buildJsonObject {
                    putJsonObject("update") {
                        put("name", session.shareName(docId))
                        putJsonObject("fields") {
                            putJsonObject("payload") { put("stringValue", CloudCrypto.seal(keys, docId, ChatLogic.encodeHead(head))) }
                            putJsonObject("updatedAt") {
                                putJsonObject("mapValue") {
                                    putJsonObject("fields") { putJsonObject("v") { put("integerValue", version.toString()) } }
                                }
                            }
                        }
                    }
                    putJsonObject("updateMask") {
                        putJsonArray("fieldPaths") {
                            add(JsonPrimitive("payload"))
                            add(JsonPrimitive("updatedAt.v"))
                        }
                    }
                })
            }
        }.toString()
        val reply = session.commit(body)
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось отправить сообщение")
    }

    /** Отметить, что сообщения второго человека прочитаны до [upTo]. false — своей головы ещё нет. */
    fun putRead(code: String, me: Person, upTo: Long): Boolean = putMark(code, me, "r", upTo)

    /** «Печатаю»: любое новое число, 0 — перестал. false — своей головы ещё нет. */
    fun putTyping(code: String, me: Person, value: Long): Boolean = putMark(code, me, "t", value)

    private fun putMark(code: String, me: Person, field: String, value: Long): Boolean {
        val keys = keys(code)
        val body = buildJsonObject {
            putJsonArray("writes") {
                add(buildJsonObject {
                    putJsonObject("update") {
                        put("name", session.shareName(keys.chatHead(me)))
                        putJsonObject("fields") {
                            putJsonObject("updatedAt") {
                                putJsonObject("mapValue") {
                                    putJsonObject("fields") { putJsonObject(field) { put("integerValue", value.toString()) } }
                                }
                            }
                        }
                    }
                    putJsonObject("updateMask") {
                        putJsonArray("fieldPaths") { add(JsonPrimitive("updatedAt.$field")) }
                    }
                    // Без головы отметку не пишем: правила базы и так не пустят документ без сообщений.
                    putJsonObject("currentDocument") { put("exists", true) }
                })
            }
        }.toString()
        val reply = session.commit(body)
        if (reply.code == 404 || reply.code == 400 && reply.body.contains("NOT_FOUND")) return false
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось отправить отметку")
        return true
    }

    /** Положить фото (уже сжатое) под номером [id]. */
    fun putFile(code: String, id: String, bytes: ByteArray, now: Long) {
        val keys = keys(code)
        val docId = keys.chatFile(id)
        val sealed = CloudCrypto.sealBytes(keys, docId, bytes)
        if (sealed.length >= MAX_PAYLOAD) throw CloudException("Фото слишком большое")
        val body = buildJsonObject { putJsonArray("writes") { add(plainWrite(docId, sealed, now)) } }.toString()
        val reply = session.commit(body)
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось отправить фото")
    }

    /** Стереть фото в облаке (удалять документы правила не дают, поэтому он становится пустым). */
    fun wipeFile(code: String, id: String, now: Long) {
        val keys = keys(code)
        val body = buildJsonObject { putJsonArray("writes") { add(plainWrite(keys.chatFile(id), "", now)) } }.toString()
        val reply = session.commit(body)
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось стереть фото")
    }

    /** Скачать фото. null — его нет или оно стёрто. */
    fun file(code: String, id: String): ByteArray? {
        val keys = keys(code)
        val docId = keys.chatFile(id)
        val reply = session.getShare(docId)
        if (reply.code == 404) return null
        if (reply.code !in 200..299) throw session.failure(reply, "Не удалось получить фото")
        val sealed = fields(reply).stringOf("payload", "stringValue").orEmpty()
        if (sealed.isEmpty()) return null
        return CloudCrypto.openBytes(keys, docId, sealed) ?: throw CloudException("Фото не расшифровалось", reply.code)
    }

    private fun keys(code: String): CloudKeys {
        require(PairCode.isValid(code)) { "Неверный код связи" }
        return CloudCrypto.derive(code)
    }

    private fun plainWrite(docId: String, payload: String, updatedAt: Long): JsonObject = buildJsonObject {
        putJsonObject("update") {
            put("name", session.shareName(docId))
            putJsonObject("fields") {
                putJsonObject("payload") { put("stringValue", payload) }
                putJsonObject("updatedAt") { put("integerValue", updatedAt.toString()) }
            }
        }
    }

    private fun fields(reply: HttpReply): JsonObject =
        runCatching { json.parseToJsonElement(reply.body).jsonObject["fields"]?.jsonObject }.getOrNull()
            ?: throw CloudException("Облако ответило непонятно", reply.code)

    private fun openPayload(keys: CloudKeys, docId: String, fields: JsonObject): String? {
        val sealed = fields.stringOf("payload", "stringValue") ?: return null
        return CloudCrypto.open(keys, docId, sealed)
    }

    private fun markOf(fields: JsonObject): ChatMark {
        val map = runCatching { fields["updatedAt"]?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject }.getOrNull()
        fun number(name: String): Long = map?.stringOf(name, "integerValue")?.toLongOrNull() ?: 0L
        return ChatMark(version = number("v"), read = number("r"), typing = number("t"))
    }

    private fun JsonObject.stringOf(field: String, type: String): String? =
        runCatching { this[field]?.jsonObject?.get(type)?.jsonPrimitive?.contentOrNull }.getOrNull()

    companion object {
        /** Правила базы: поле payload короче 300 000 знаков. */
        const val MAX_PAYLOAD = 299_000

        /** Фото после сжатия не больше этого, чтобы в зашифрованном виде пройти правила. */
        const val MAX_PHOTO_BYTES = 215_000
    }
}

/** Копия сообщения для пуша: шифруется тем же ключом, а проверяемое имя у неё своё, чтобы её нельзя было выдать за голову. */
object ChatPreviewSeal {

    private fun docId(keys: CloudKeys, from: Person): String = keys.chatHead(from) + "#push"

    fun seal(code: String, from: Person, preview: ChatPreview): String {
        val keys = CloudCrypto.derive(code)
        return CloudCrypto.seal(keys, docId(keys, from), ChatLogic.encodePreview(preview))
    }

    fun open(code: String, from: Person, sealed: String): ChatPreview? {
        if (!PairCode.isValid(code)) return null
        val keys = CloudCrypto.derive(code)
        return CloudCrypto.open(keys, docId(keys, from), sealed)?.let(ChatLogic::decodePreview)
    }
}
