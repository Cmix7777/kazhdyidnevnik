package io.github.cmix7777.kazhdyidnevnik.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Переходы в системные настройки, чтобы телефон не мешал напоминаниям. */
object SystemSettings {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return ReminderScheduler.canScheduleExact(alarmManager)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!start(context, intent)) openAppDetails(context)
    }

    /** Автозапуск на Xiaomi / HyperOS. На других телефонах — страница приложения. */
    fun openAutostart(context: Context) {
        val miui = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )
        if (!start(context, miui)) openAppDetails(context)
    }

    @SuppressLint("BatteryLife")
    fun requestNoBatteryLimits(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        if (!start(context, intent)) openAppDetails(context)
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
            if (start(context, intent)) return
        }
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
