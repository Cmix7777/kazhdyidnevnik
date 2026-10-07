package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Версия приложения на GitHub. [sizeBytes] = 0, если размер неизвестен. */
data class Release(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long = 0,
)

object ReleaseParser {

    const val REPO = "Cmix7777/kazhdyidnevnik"
    private const val APK_NAME = "kazhdyidnevnik.apk"
    private val tagRegex = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

    /** Номер сборки из тега: «v1.0.7» -> 7. */
    fun versionCode(tag: String): Int? = tagRegex.find(tag)?.groupValues?.get(3)?.toIntOrNull()

    fun apkUrl(tag: String): String = "https://github.com/$REPO/releases/download/$tag/$APK_NAME"

    /** Ответ api.github.com/repos/…/releases/latest. */
    fun fromApi(json: String): Release? {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val code = versionCode(tag) ?: return null
        val asset = root["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == APK_NAME }
        return Release(
            versionCode = code,
            versionName = tag.removePrefix("v"),
            notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
            apkUrl = asset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: apkUrl(tag),
            sizeBytes = asset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    /** Адрес, на который GitHub перенаправляет /releases/latest: «…/releases/tag/v1.0.7». */
    fun fromTagUrl(url: String): Release? {
        val tag = url.substringAfterLast("/tag/", "").substringBefore('?').takeIf { it.isNotEmpty() } ?: return null
        val code = versionCode(tag) ?: return null
        return Release(code, tag.removePrefix("v"), notes = "", apkUrl = apkUrl(tag))
    }
}
