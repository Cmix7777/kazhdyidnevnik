package io.github.cmix7777.kazhdyidnevnik.notify

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Будильник напоминаний. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_ALARM) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler.handleAlarm(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Кнопка «Сделал» в уведомлении о блоке учёбы. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        ProgressRepository.markDone(context.filesDir, key, minutes)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        const val ACTION_DONE = "io.github.cmix7777.kazhdyidnevnik.DONE"
        const val EXTRA_KEY = "key"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** После перезагрузки, обновления приложения или смены времени заново ставит будильник. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        val app = context.applicationContext
        ReminderScheduler.scheduleNext(app)
        ScheduleCheckWorker.schedulePeriodic(app)
    }

    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    )
}
