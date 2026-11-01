package io.github.cmix7777.kazhdyidnevnik.service

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.cmix7777.kazhdyidnevnik.data.CloudConfig
import io.github.cmix7777.kazhdyidnevnik.notify.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Мгновенные уведомления о сообщениях без своего сервера. Пуш через Firebase может отправить только тот,
 * у кого есть ключ проекта, поэтому посредником служит маленький бесплатный скрипт Google Apps Script
 * на аккаунте владельца (папка push в репозитории): телефон передаёт ему токен второго телефона и
 * зашифрованную копию сообщения, скрипт отдаёт её Firebase. Прочитать сообщение не может ни скрипт, ни Google.
 * Ссылку на скрипт вставляют один раз на любом телефоне, второй получает её сам вместе с перепиской.
 */
object ChatPush {

    enum class Sent { OK, UNREGISTERED, FAILED }

    private val hookPattern = Regex("^https://script\\.google\\.com/(?:a/[^/\\s]+/)?macros/s/[A-Za-z0-9_-]{20,}/exec$")
    private val hookInText = Regex("https://script\\.google\\.com/\\S+?/exec")
    private val appIdPattern = Regex("^1:\\d+:android:[0-9a-f]+$")
    private val json = Json { ignoreUnknownKeys = true }

    /** Достать ссылку пересылки из любого текста. null — её нет. */
    fun parseHook(text: String): String? = hookInText.find(text.trim())?.value?.takeIf { hookPattern.matches(it) }

    /** Номер приложения в Firebase, который сообщает пересылка. null — по ссылке не она. */
    fun fetchAppId(hook: String): String? {
        val reply = JsoupTransport.get(hook)
        if (reply.code !in 200..299) return null
        val obj = runCatching { json.parseToJsonElement(reply.body).jsonObject }.getOrNull() ?: return null
        if (obj.text("app") != "kd-push") return null
        return obj.text("appId")?.takeIf { appIdPattern.matches(it) }
    }

    /** Настроить по ссылке из буфера. Возвращает, что сказать человеку. */
    suspend fun configure(context: Context, text: String): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val hook = parseHook(text)
            ?: return@withContext "В буфере нет ссылки вида https://script.google.com/macros/s/…/exec. Скопируй её целиком."
        val appId = runCatching { fetchAppId(hook) }.getOrNull()
            ?: return@withContext "По ссылке не отвечает пересылка Каждыйдневника. Проверь, что это «Веб-приложение» " +
                "с доступом «Все», а в свойствах скрипта есть APP_ID и SERVICE_ACCOUNT."
        val settings = AppSettings(app)
        settings.pushHook = hook
        settings.pushAppId = appId
        settings.pushProblem = ""
        init(app)
        ChatSync.pushChanged(app)
        "Готово: уведомления о сообщениях будут приходить сразу. Второй телефон подхватит настройку сам."
    }

    /** Второй телефон уже настроил пересылку: берём её ссылку себе. */
    fun adopt(context: Context, hook: String) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        if (settings.pushHook != null || parseHook(hook) != hook) return
        val appId = runCatching { fetchAppId(hook) }.getOrNull() ?: return
        settings.pushHook = hook
        settings.pushAppId = appId
        init(app)
        ChatSync.pushChanged(app)
    }

    /** Включить Firebase (если настроено) и узнать свой токен пушей. Безопасно вызывать сколько угодно раз. */
    fun init(context: Context) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        val appId = settings.pushAppId ?: return
        try {
            if (FirebaseApp.getApps(app).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setApiKey(CloudConfig.API_KEY)
                    .setProjectId(CloudConfig.PROJECT_ID)
                    .setGcmSenderId(appId.split(':')[1])
                    .build()
                FirebaseApp.initializeApp(app, options)
            }
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    tokenChanged(app, task.result)
                } else {
                    settings.pushProblem = "Firebase не выдал токен: ${task.exception?.message ?: "нет Google Play или сети"}"
                }
            }
        } catch (e: Exception) {
            settings.pushProblem = "Не получилось включить пуши: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun tokenChanged(context: Context, token: String?) {
        if (token.isNullOrBlank()) return
        val settings = AppSettings(context)
        if (settings.pushToken == token) return
        settings.pushToken = token
        settings.pushProblem = ""
        ChatSync.pushChanged(context)
    }

    /** Отправить пуш через пересылку. */
    fun send(hook: String, token: String, data: Map<String, String>): Sent {
        val body = buildJsonObject {
            put("to", token)
            putJsonObject("data") { data.forEach { (key, value) -> put(key, value) } }
        }.toString()
        val reply = runCatching { JsoupTransport.post(hook, body, "application/json") }.getOrNull() ?: return Sent.FAILED
        val obj = runCatching { json.parseToJsonElement(reply.body).jsonObject }.getOrNull() ?: return Sent.FAILED
        return when {
            obj["ok"]?.jsonPrimitive?.booleanOrNull == true -> Sent.OK
            obj.text("error") == "unregistered" -> Sent.UNREGISTERED
            else -> Sent.FAILED
        }
    }

    private fun JsonObject.text(key: String): String? = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/** Приём пушей Firebase: новое сообщение или новый токен этого телефона. */
class ChatPushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        ChatPush.tokenChanged(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ChatSync.onPush(applicationContext, message.data)
    }
}
