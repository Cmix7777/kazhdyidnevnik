package io.github.cmix7777.kazhdyidnevnik.notify

import android.content.Context
import androidx.core.content.edit
import io.github.cmix7777.kazhdyidnevnik.data.PartnerAlerts
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.Release
import io.github.cmix7777.kazhdyidnevnik.data.ReminderSettings
import java.time.LocalDate
import java.time.LocalTime

/** Настройки и служебные отметки приложения (хранятся на телефоне). */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var reminders: ReminderSettings
        get() {
            val defaults = ReminderSettings()
            return ReminderSettings(
                changes = prefs.getBoolean(CHANGES, defaults.changes),
                morning = prefs.getBoolean(MORNING, defaults.morning),
                morningTime = timeOf(prefs.getInt(MORNING_TIME, minutesOf(defaults.morningTime))),
                blocks = prefs.getBoolean(BLOCKS, defaults.blocks),
                evening = prefs.getBoolean(EVENING, defaults.evening),
                eveningTime = timeOf(prefs.getInt(EVENING_TIME, minutesOf(defaults.eveningTime))),
                weather = prefs.getBoolean(WEATHER, defaults.weather),
                partner = PartnerAlerts(
                    changes = prefs.getBoolean(PARTNER_CHANGES, false),
                    morning = prefs.getBoolean(PARTNER_MORNING, false),
                    study = prefs.getBoolean(PARTNER_STUDY, false),
                    weather = prefs.getBoolean(PARTNER_WEATHER, false),
                ),
            )
        }
        set(value) {
            prefs.edit {
                putBoolean(CHANGES, value.changes)
                putBoolean(MORNING, value.morning)
                putInt(MORNING_TIME, minutesOf(value.morningTime))
                putBoolean(BLOCKS, value.blocks)
                putBoolean(EVENING, value.evening)
                putInt(EVENING_TIME, minutesOf(value.eveningTime))
                putBoolean(WEATHER, value.weather)
                putBoolean(PARTNER_CHANGES, value.partner.changes)
                putBoolean(PARTNER_MORNING, value.partner.morning)
                putBoolean(PARTNER_STUDY, value.partner.study)
                putBoolean(PARTNER_WEATHER, value.partner.weather)
            }
        }

    /**
     * Чей это телефон. null — ещё не выбрано (приложение спросит при первом запуске).
     * На телефоне, где приложение стояло до версии на двоих, это Айзат.
     */
    var owner: Person?
        get() = Person.fromId(prefs.getString(OWNER, null))
            ?: if (prefs.contains(NOTIFICATIONS_ASKED) || prefs.contains(LAST_FIRED)) Person.AIZAT else null
        set(value) = prefs.edit { putString(OWNER, value?.id) }

    /** До какого момента напоминания уже показаны. */
    var lastFiredMillis: Long
        get() = prefs.getLong(LAST_FIRED, 0L)
        set(value) = prefs.edit { putLong(LAST_FIRED, value) }

    /** Когда сработает следующее напоминание (для экрана настроек). */
    var nextAlarmMillis: Long
        get() = prefs.getLong(NEXT_ALARM, 0L)
        set(value) = prefs.edit { putLong(NEXT_ALARM, value) }

    var notificationsAsked: Boolean
        get() = prefs.getBoolean(NOTIFICATIONS_ASKED, false)
        set(value) = prefs.edit { putBoolean(NOTIFICATIONS_ASKED, value) }

    val lastCheckMillis: Long get() = prefs.getLong(LAST_CHECK, 0L)

    val lastCheckResult: String get() = prefs.getString(LAST_CHECK_RESULT, null).orEmpty()

    fun recordCheck(millis: Long, result: String) {
        prefs.edit {
            putLong(LAST_CHECK, millis)
            putString(LAST_CHECK_RESULT, result)
        }
    }

    /** Когда последний раз проверялись обновления приложения. */
    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit { putLong(LAST_UPDATE_CHECK, value) }

    /** Последняя найденная на GitHub версия (чтобы показать её и без сети). */
    var knownRelease: Release?
        get() {
            val code = prefs.getInt(RELEASE_CODE, 0)
            val url = prefs.getString(RELEASE_URL, null)
            if (code == 0 || url == null) return null
            return Release(
                versionCode = code,
                versionName = prefs.getString(RELEASE_NAME, null).orEmpty(),
                notes = prefs.getString(RELEASE_NOTES, null).orEmpty(),
                apkUrl = url,
                sizeBytes = prefs.getLong(RELEASE_SIZE, 0L),
            )
        }
        set(value) {
            prefs.edit {
                if (value == null) {
                    remove(RELEASE_CODE)
                    remove(RELEASE_NAME)
                    remove(RELEASE_NOTES)
                    remove(RELEASE_URL)
                    remove(RELEASE_SIZE)
                } else {
                    putInt(RELEASE_CODE, value.versionCode)
                    putString(RELEASE_NAME, value.versionName)
                    putString(RELEASE_NOTES, value.notes)
                    putString(RELEASE_URL, value.apkUrl)
                    putLong(RELEASE_SIZE, value.sizeBytes)
                }
            }
        }

    /** О какой версии уже было уведомление. */
    var updateNotifiedFor: Int
        get() = prefs.getInt(UPDATE_NOTIFIED, 0)
        set(value) = prefs.edit { putInt(UPDATE_NOTIFIED, value) }

    /** Файл автокопии в «Загрузках» и время последней автокопии. */
    var autoBackupUri: String?
        get() = prefs.getString(AUTO_BACKUP_URI, null)
        set(value) = prefs.edit { putString(AUTO_BACKUP_URI, value) }

    var lastAutoBackupMillis: Long
        get() = prefs.getLong(LAST_AUTO_BACKUP, 0L)
        set(value) = prefs.edit { putLong(LAST_AUTO_BACKUP, value) }

    /** Сайт один раз отдал пустую неделю вместо пар — ждём подтверждения следующей проверкой. */
    fun isEmptyPending(person: Person, weekStart: LocalDate): Boolean =
        prefs.getBoolean(emptyKey(person, weekStart), false)

    fun setEmptyPending(person: Person, weekStart: LocalDate, pending: Boolean) {
        prefs.edit {
            if (pending) putBoolean(emptyKey(person, weekStart), true) else remove(emptyKey(person, weekStart))
        }
    }

    // У Айзата ключ прежний, чтобы не потерять отметку после обновления.
    private fun emptyKey(person: Person, weekStart: LocalDate): String =
        if (person == Person.AIZAT) "$EMPTY_PREFIX$weekStart" else "$EMPTY_PREFIX${person.id}-$weekStart"

    private companion object {
        const val CHANGES = "changes"
        const val MORNING = "morning"
        const val MORNING_TIME = "morningTime"
        const val BLOCKS = "blocks"
        const val EVENING = "evening"
        const val EVENING_TIME = "eveningTime"
        const val WEATHER = "weather"
        const val PARTNER_CHANGES = "partnerChanges"
        const val PARTNER_MORNING = "partnerMorning"
        const val PARTNER_STUDY = "partnerStudy"
        const val PARTNER_WEATHER = "partnerWeather"
        const val OWNER = "owner"
        const val LAST_FIRED = "lastFired"
        const val NEXT_ALARM = "nextAlarm"
        const val NOTIFICATIONS_ASKED = "notificationsAsked"
        const val LAST_CHECK = "lastCheck"
        const val LAST_CHECK_RESULT = "lastCheckResult"
        const val EMPTY_PREFIX = "emptyPending-"
        const val LAST_UPDATE_CHECK = "lastUpdateCheck"
        const val RELEASE_CODE = "releaseCode"
        const val RELEASE_NAME = "releaseName"
        const val RELEASE_NOTES = "releaseNotes"
        const val RELEASE_URL = "releaseUrl"
        const val RELEASE_SIZE = "releaseSize"
        const val UPDATE_NOTIFIED = "updateNotified"
        const val AUTO_BACKUP_URI = "autoBackupUri"
        const val LAST_AUTO_BACKUP = "lastAutoBackup"

        fun minutesOf(time: LocalTime): Int = time.hour * 60 + time.minute

        fun timeOf(minutes: Int): LocalTime = LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60)
    }
}
