package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Random

/**
 * Куда подключаться: бесплатный проект Firebase этого приложения (тариф Spark, карта не нужна).
 * Ключ не секрет, он только называет проект. Защищают данные правила базы: читать и писать
 * можно только документ, чьё имя начинается с длинного случайного кода связи.
 */
object CloudConfig {
    const val PROJECT_ID = "kazhdyidnevnik"
    const val API_KEY = "AIzaSyCMviofSnOI4XrcRfVvaLh_G1giqyozX04"
}

/**
 * Код связи двух телефонов: 20 случайных знаков (около 100 бит), показывается как kds-xxxxx-xxxxx-xxxxx-xxxxx.
 * Его создаёт один телефон, второй вставляет. Из кода выводятся имя документа и ключ шифрования
 * ([CloudCrypto]), сам код в облако не уходит. Угадать его нельзя, поэтому чужой документ не найти и не прочитать.
 * Коды kdp-... были у версии без шифрования: сервер их видел, поэтому они больше не годятся.
 */
object PairCode {

    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    const val LENGTH = 20
    private const val GROUP = 5
    private const val GROUPS = "([a-hj-km-np-z2-9]{5})-([a-hj-km-np-z2-9]{5})-([a-hj-km-np-z2-9]{5})-([a-hj-km-np-z2-9]{5})"
    private val regex = Regex("kds-$GROUPS", RegexOption.IGNORE_CASE)
    private val legacyRegex = Regex("kdp-$GROUPS", RegexOption.IGNORE_CASE)

    fun generate(random: Random = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** Красивый вид для людей: kds-xxxxx-xxxxx-xxxxx-xxxxx. */
    fun display(code: String): String = "kds-" + code.chunked(GROUP).joinToString("-")

    /** Достаёт код из любого текста (сообщения целиком). null — кода нет. */
    fun parse(text: String): String? =
        regex.find(text)?.groupValues?.drop(1)?.joinToString("")?.lowercase()

    /** В тексте есть код старого вида (без шифрования), нового кода при этом нет. */
    fun isLegacy(text: String): Boolean = parse(text) == null && legacyRegex.containsMatchIn(text)

    /** Сообщение, которое уходит второму человеку в мессенджер. */
    fun message(from: Person, code: String): String =
        "Код связи Каждогодневника (${from.shortName}): ${display(code)}\n" +
            "Нужна последняя версия приложения. Скопируй всё сообщение, открой Настройки и нажми «Вставить код» в разделе связи."

    /** Короткий отпечаток данных: если он не изменился, заново отправлять нечего. */
    fun fingerprint(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Ответ сервера: код и тело. */
data class HttpReply(val code: Int, val body: String)

/** Как отправлять запросы. В приложении это Jsoup, в тестах подставка. */
interface HttpTransport {
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpReply
    fun post(url: String, body: String, contentType: String, headers: Map<String, String> = emptyMap()): HttpReply
}

/** Вход без аккаунта, выданный Firebase: короткий пропуск и ключ, чтобы его продлевать. */
data class CloudToken(val idToken: String, val refreshToken: String, val expiresAtMillis: Long)

/** Где хранить пропуск между запусками. */
interface TokenStore {
    fun load(): CloudToken?
    fun save(token: CloudToken?)
}

/** Не получилось поговорить с облаком. [code] — ответ сервера, 0 — не дошли до него. */
class CloudException(message: String, val code: Int = 0, cause: Throwable? = null) : Exception(message, cause)

/**
 * Обмен делами через Firestore по REST, без библиотек Google. Каждый человек кладёт свои дела в
 * документ shares/<имя из кода>-<кто> и читает документ второго. Дела шифруются на телефоне
 * ([CloudCrypto]), вход анонимный, без паролей.
 */
class CloudShares(
    private val http: HttpTransport,
    private val tokens: TokenStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val projectId: String = CloudConfig.PROJECT_ID,
    private val apiKey: String = CloudConfig.API_KEY,
) {

    /** Что лежит в облаке: набор дел (уже расшифрованный) в виде строки и время, когда его положили. */
    data class Remote(val payload: String, val updatedAt: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val database = "projects/$projectId/databases/(default)/documents"

    /** Положить (или заменить) дела [owner] в паре [code]. */
    fun publish(code: String, owner: Person, payload: String, updatedAt: Long) {
        require(PairCode.isValid(code)) { "Неверный код связи" }
        val keys = CloudCrypto.derive(code)
        val docId = keys.docId(owner)
        val name = "$database/shares/$docId"
        val sealed = CloudCrypto.seal(keys, docId, payload)
        val body = buildJsonObject {
            put("writes", buildJsonArray {
                add(buildJsonObject {
                    put("update", buildJsonObject {
                        put("name", name)
                        put("fields", buildJsonObject {
                            put("payload", buildJsonObject { put("stringValue", sealed) })
                            put("updatedAt", buildJsonObject { put("integerValue", updatedAt.toString()) })
                        })
                    })
                })
            })
        }.toString()
        val reply = authorized { token ->
            http.post(
                "https://firestore.googleapis.com/v1/$database:commit",
                body,
                JSON,
                mapOf("Authorization" to "Bearer $token"),
            )
        }
        if (reply.code !in 200..299) throw failure(reply, "Не удалось отправить дела")
    }

    /** Забрать дела [person] из пары [code]. null — он ещё ничего не отправлял. */
    fun fetch(code: String, person: Person): Remote? {
        require(PairCode.isValid(code)) { "Неверный код связи" }
        val keys = CloudCrypto.derive(code)
        val docId = keys.docId(person)
        val reply = authorized { token ->
            http.get(
                "https://firestore.googleapis.com/v1/$database/shares/$docId",
                mapOf("Authorization" to "Bearer $token"),
            )
        }
        if (reply.code == 404) return null
        if (reply.code !in 200..299) throw failure(reply, "Не удалось получить дела")
        val fields = runCatching { json.parseToJsonElement(reply.body).jsonObject["fields"]?.jsonObject }.getOrNull()
            ?: throw CloudException("Облако ответило непонятно", reply.code)
        val sealed = fields.stringOf("payload", "stringValue") ?: throw CloudException("В облаке нет дел", reply.code)
        val payload = CloudCrypto.open(keys, docId, sealed)
            ?: throw CloudException("Дела в облаке не расшифровались: код не тот или данные повреждены", reply.code)
        val updated = fields.stringOf("updatedAt", "integerValue")?.toLongOrNull() ?: 0L
        return Remote(payload, updated)
    }

    /** Выполнить запрос с пропуском; если пропуск не принят (ответ 401), входим заново и повторяем один раз. */
    private fun authorized(request: (String) -> HttpReply): HttpReply {
        val first = request(token(forceNew = false))
        if (first.code != UNAUTHORIZED) return first
        tokens.save(null)
        return request(token(forceNew = true))
    }

    private fun token(forceNew: Boolean): String {
        val saved = if (forceNew) null else tokens.load()
        if (saved != null && saved.expiresAtMillis - now() > MARGIN_MS) return saved.idToken
        if (saved != null) {
            val refreshed = runCatching { refresh(saved) }.getOrNull()
            if (refreshed != null) {
                tokens.save(refreshed)
                return refreshed.idToken
            }
        }
        val created = signUp()
        tokens.save(created)
        return created.idToken
    }

    private fun signUp(): CloudToken {
        val reply = http.post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
            """{"returnSecureToken":true}""",
            JSON,
        )
        if (reply.code !in 200..299) throw failure(reply, "Не удалось войти в облако")
        val obj = parse(reply)
        val id = obj.string("idToken")
        val refresh = obj.string("refreshToken")
        val seconds = obj.string("expiresIn")?.toLongOrNull() ?: DEFAULT_SECONDS
        if (id == null || refresh == null) throw CloudException("Облако не выдало вход", reply.code)
        return CloudToken(id, refresh, now() + seconds * 1000)
    }

    private fun refresh(old: CloudToken): CloudToken? {
        val reply = http.post(
            "https://securetoken.googleapis.com/v1/token?key=$apiKey",
            "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(old.refreshToken, "UTF-8"),
            FORM,
        )
        if (reply.code !in 200..299) return null
        val obj = parse(reply)
        val id = obj.string("id_token") ?: return null
        val refresh = obj.string("refresh_token") ?: old.refreshToken
        val seconds = obj.string("expires_in")?.toLongOrNull() ?: DEFAULT_SECONDS
        return CloudToken(id, refresh, now() + seconds * 1000)
    }

    private fun parse(reply: HttpReply): JsonObject =
        runCatching { json.parseToJsonElement(reply.body).jsonObject }.getOrElse {
            throw CloudException("Облако ответило непонятно", reply.code, it)
        }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringOf(field: String, type: String): String? =
        runCatching { this[field]?.jsonObject?.get(type)?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun failure(reply: HttpReply, what: String): CloudException {
        val reason = runCatching { json.parseToJsonElement(reply.body).jsonObject["error"]?.jsonObject?.get("message") }
            .getOrNull()?.jsonPrimitive?.contentOrNull
        val tail = when (reply.code) {
            403 -> ": доступ закрыт правилами базы"
            429 -> ": слишком много запросов, попробуй позже"
            in 500..599 -> ": у Google сбой, попробуй позже"
            else -> if (reason != null) ": $reason" else ""
        }
        return CloudException("$what (${reply.code})$tail", reply.code)
    }

    private companion object {
        const val JSON = "application/json"
        const val FORM = "application/x-www-form-urlencoded"
        const val UNAUTHORIZED = 401
        const val MARGIN_MS = 60_000L
        const val DEFAULT_SECONDS = 3600L
    }
}
