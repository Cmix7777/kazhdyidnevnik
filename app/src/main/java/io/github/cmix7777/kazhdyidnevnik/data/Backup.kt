package io.github.cmix7777.kazhdyidnevnik.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.LocalTime

/** Резервная копия: отметки «сделал» и настройки уведомлений в одном JSON-файле. */
object Backup {

    const val APP = "kazhdyidnevnik"
    private const val FORMAT = 1

    data class Content(val done: Map<String, Int>, val settings: ReminderSettings?)

    @Serializable
    private data class SettingsDto(
        val changes: Boolean,
        val morning: Boolean,
        val morningTime: String,
        val blocks: Boolean,
        val evening: Boolean,
        val eveningTime: String,
        val weather: Boolean = true,
        val partner: PartnerDto? = null,
    )

    @Serializable
    private data class PartnerDto(
        val changes: Boolean = false,
        val morning: Boolean = false,
        val study: Boolean = false,
        val weather: Boolean = false,
    )

    @Serializable
    private data class BackupDto(
        val app: String,
        val format: Int,
        val createdAt: String,
        val done: Map<String, Int>,
        val settings: SettingsDto? = null,
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(done: Map<String, Int>, settings: ReminderSettings, createdAt: LocalDateTime): String {
        val dto = BackupDto(
            app = APP,
            format = FORMAT,
            createdAt = createdAt.withNano(0).toString(),
            done = done.toSortedMap(),
            settings = SettingsDto(
                changes = settings.changes,
                morning = settings.morning,
                morningTime = settings.morningTime.toString(),
                blocks = settings.blocks,
                evening = settings.evening,
                eveningTime = settings.eveningTime.toString(),
                weather = settings.weather,
                partner = PartnerDto(
                    changes = settings.partner.changes,
                    morning = settings.partner.morning,
                    study = settings.partner.study,
                    weather = settings.partner.weather,
                ),
            ),
        )
        return json.encodeToString(BackupDto.serializer(), dto)
    }

    /** null — файл не похож на копию Каждыйдневника. */
    fun decode(text: String): Content? {
        val dto = runCatching { json.decodeFromString(BackupDto.serializer(), text) }.getOrNull() ?: return null
        if (dto.app != APP) return null
        val settings = dto.settings?.let { s ->
            runCatching {
                ReminderSettings(
                    changes = s.changes,
                    morning = s.morning,
                    morningTime = LocalTime.parse(s.morningTime),
                    blocks = s.blocks,
                    evening = s.evening,
                    eveningTime = LocalTime.parse(s.eveningTime),
                    weather = s.weather,
                    partner = s.partner?.let { p ->
                        PartnerAlerts(changes = p.changes, morning = p.morning, study = p.study, weather = p.weather)
                    } ?: PartnerAlerts(),
                )
            }.getOrNull()
        }
        return Content(dto.done.filterValues { it >= 0 }, settings)
    }

    /** Объединить отметки: ничего не теряется, при совпадении берётся большее число минут. */
    fun merge(current: Map<String, Int>, incoming: Map<String, Int>): Map<String, Int> =
        (current.keys + incoming.keys).associateWith { key ->
            maxOf(current[key] ?: 0, incoming[key] ?: 0)
        }
}
