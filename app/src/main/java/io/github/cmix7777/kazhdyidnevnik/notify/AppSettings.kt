package io.github.cmix7777.kazhdyidnevnik.notify

import android.content.Context
import androidx.core.content.edit
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
            }
        }

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

    /** Сайт один раз отдал пустую неделю вместо пар — ждём подтверждения следующей проверкой. */
    fun isEmptyPending(weekStart: LocalDate): Boolean = prefs.getBoolean("$EMPTY_PREFIX$weekStart", false)

    fun setEmptyPending(weekStart: LocalDate, pending: Boolean) {
        prefs.edit {
            if (pending) putBoolean("$EMPTY_PREFIX$weekStart", true) else remove("$EMPTY_PREFIX$weekStart")
        }
    }

    private companion object {
        const val CHANGES = "changes"
        const val MORNING = "morning"
        const val MORNING_TIME = "morningTime"
        const val BLOCKS = "blocks"
        const val EVENING = "evening"
        const val EVENING_TIME = "eveningTime"
        const val LAST_FIRED = "lastFired"
        const val NEXT_ALARM = "nextAlarm"
        const val NOTIFICATIONS_ASKED = "notificationsAsked"
        const val LAST_CHECK = "lastCheck"
        const val LAST_CHECK_RESULT = "lastCheckResult"
        const val EMPTY_PREFIX = "emptyPending-"

        fun minutesOf(time: LocalTime): Int = time.hour * 60 + time.minute

        fun timeOf(minutes: Int): LocalTime = LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60)
    }
}
