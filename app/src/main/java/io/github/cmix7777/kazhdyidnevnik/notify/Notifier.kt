package io.github.cmix7777.kazhdyidnevnik.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.cmix7777.kazhdyidnevnik.MainActivity
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.data.PlanBlock
import io.github.cmix7777.kazhdyidnevnik.data.Release
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleChange
import io.github.cmix7777.kazhdyidnevnik.data.ScheduleDiff
import io.github.cmix7777.kazhdyidnevnik.data.Summaries
import io.github.cmix7777.kazhdyidnevnik.formatWeekRange
import java.time.LocalDate

/** Все уведомления приложения. */
object Notifier {

    private const val CHANNEL_CHANGES = "schedule_changes"
    private const val CHANNEL_MORNING = "morning"
    private const val CHANNEL_STUDY = "study"
    private const val CHANNEL_UPDATES = "updates"
    private const val CHANNEL_WEATHER = "weather"

    private const val ID_MORNING = 10
    private const val ID_EVENING = 11
    private const val ID_TEST = 12
    private const val ID_UPDATE = 13
    private const val ID_WEATHER = 14
    private const val MAX_LINES = 8

    /** Уведомления про второго человека получают свои номера, чтобы не заменять свои. */
    private const val PARTNER_OFFSET = 1000
    private const val ACCENT_AIZAT = 0xFFA855F7.toInt()
    private const val ACCENT_NASTYA = 0xFFA77BEA.toInt()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_CHANGES,
                    "Изменения в расписании",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Пары перенесли, отменили или добавили" },
                NotificationChannel(
                    CHANNEL_MORNING,
                    "Утренняя сводка",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Что сегодня: пары, работа, учёба, дедлайны" },
                NotificationChannel(
                    CHANNEL_STUDY,
                    "Учёба",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Начало блоков учёбы и вечерняя отметка" },
                NotificationChannel(
                    CHANNEL_WEATHER,
                    "Погода на завтра",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Вечером: погода при выходе, что надеть, предупреждения" },
                NotificationChannel(
                    CHANNEL_UPDATES,
                    "Обновления приложения",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Вышла новая версия Каждыйдневника" },
            ),
        )
    }

    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Расписание [person] изменилось. [owner] — чей телефон: про второго человека заголовок с именем. */
    fun scheduleChanged(
        context: Context,
        person: Person,
        owner: Person?,
        weekStart: LocalDate,
        changes: List<ScheduleChange>,
    ) {
        val own = person == owner
        val lines = changes.take(MAX_LINES).map(ScheduleDiff::describe).toMutableList()
        if (changes.size > MAX_LINES) lines += "и ещё ${changes.size - MAX_LINES}"
        val title = if (own) "Расписание изменилось" else "Расписание ${person.genitive} изменилось"
        val notification = builder(context, CHANNEL_CHANGES, title, lines.joinToString("\n"))
            .setSubText(formatWeekRange(weekStart))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        post(context, (if (own) 2000 else 3000) + (weekStart.toEpochDay() % 1000).toInt(), notification)
    }

    fun morning(context: Context, title: String, text: String, partner: Boolean = false) {
        val id = ID_MORNING + if (partner) PARTNER_OFFSET else 0
        post(context, id, builder(context, CHANNEL_MORNING, title, text).build())
    }

    /** Начало блока учёбы. Для своего — кнопка «Сделал», про второго человека — просто напоминание. */
    fun block(context: Context, date: LocalDate, block: PlanBlock, partner: Person? = null) {
        val id = 100 + block.kind.ordinal + if (partner != null) PARTNER_OFFSET else 0
        val text = Summaries.block(block)
        if (partner != null) {
            val notification = builder(context, CHANNEL_STUDY, "${partner.shortName}: ${block.title}", text)
                .setTimeoutAfter((block.minutes + 30) * 60_000L)
                .build()
            post(context, id, notification)
            return
        }
        val done = Intent(context, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_DONE)
            .putExtra(ActionReceiver.EXTRA_KEY, block.key(date))
            .putExtra(ActionReceiver.EXTRA_MINUTES, block.minutes)
            .putExtra(ActionReceiver.EXTRA_NOTIFICATION, id)
        val donePending = PendingIntent.getBroadcast(
            context,
            id,
            done,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = builder(context, CHANNEL_STUDY, block.title, text)
            .addAction(0, "Сделал", donePending)
            .setTimeoutAfter((block.minutes + 30) * 60_000L)
            .build()
        post(context, id, notification)
    }

    fun evening(context: Context, text: String) {
        post(context, ID_EVENING, builder(context, CHANNEL_STUDY, "Как прошёл день?", text).build())
    }

    fun weather(context: Context, title: String, text: String, partner: Boolean = false) {
        val id = ID_WEATHER + if (partner) PARTNER_OFFSET else 0
        post(context, id, builder(context, CHANNEL_WEATHER, title, text).build())
    }

    fun update(context: Context, release: Release) {
        val text = buildString {
            append("Открой приложение и нажми «Обновить».")
            if (release.notes.isNotBlank()) append("\n").append(release.notes)
        }
        post(context, ID_UPDATE, builder(context, CHANNEL_UPDATES, "Вышла версия ${release.versionName}", text).build())
    }

    fun test(context: Context) {
        val text = "Так будут выглядеть напоминания. Если ты это видишь, уведомления работают."
        post(context, ID_TEST, builder(context, CHANNEL_MORNING, "Каждыйдневник", text).build())
    }

    private fun builder(context: Context, channel: String, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (AppSettings(context).owner == Person.NASTYA) ACCENT_NASTYA else ACCENT_AIZAT)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)

    private fun openApp(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Разрешение забрали между проверкой и показом — просто пропускаем.
        }
    }
}
