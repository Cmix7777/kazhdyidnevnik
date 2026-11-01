package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Общая часть обмена делами и переписки: вход в Firestore без аккаунта (анонимный пропуск, который сам
 * продлевается) и запросы с этим пропуском. Если пропуск не приняли (ответ 401), входим заново и повторяем один раз.
 */
internal class FirestoreSession(
    private val http: HttpTransport,
    private val tokens: TokenStore,
    private val now: () -> Long,
    projectId: String,
    private val apiKey: String,
) {

    val json = Json { ignoreUnknownKeys = true }

    /** Путь к документам базы: projects/<проект>/databases/(default)/documents. */
    val database = "projects/$projectId/databases/(default)/documents"

    /** Прочитать документ shares/[docId]. [fields] — только эти поля (так ответ совсем короткий). */
    fun getShare(docId: String, fields: List<String> = emptyList()): HttpReply {
        val mask = if (fields.isEmpty()) "" else fields.joinToString("&", prefix = "?") { "mask.fieldPaths=$it" }
        return authorized { token -> http.get("$BASE$database/shares/$docId$mask", bearer(token)) }
    }

    /** Записать сразу несколько изменений (все или ни одного). */
    fun commit(body: String): HttpReply =
        authorized { token -> http.post("$BASE$database:commit", body, JSON, bearer(token)) }

    /** Полное имя документа shares/[docId] для записи. */
    fun shareName(docId: String): String = "$database/shares/$docId"

    fun authorized(request: (String) -> HttpReply): HttpReply {
        val first = request(token(forceNew = false))
        if (first.code != UNAUTHORIZED) return first
        tokens.save(null)
        return request(token(forceNew = true))
    }

    private fun bearer(token: String) = mapOf("Authorization" to "Bearer $token")

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

    fun parse(reply: HttpReply): JsonObject =
        runCatching { json.parseToJsonElement(reply.body).jsonObject }.getOrElse {
            throw CloudException("Облако ответило непонятно", reply.code, it)
        }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    fun failure(reply: HttpReply, what: String): CloudException {
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

    companion object {
        const val BASE = "https://firestore.googleapis.com/v1/"
        const val JSON = "application/json"
        const val FORM = "application/x-www-form-urlencoded"
        const val UNAUTHORIZED = 401
        const val MARGIN_MS = 60_000L
        const val DEFAULT_SECONDS = 3600L
    }
}
