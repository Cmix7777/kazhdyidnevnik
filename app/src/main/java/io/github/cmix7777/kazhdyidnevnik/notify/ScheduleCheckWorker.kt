package io.github.cmix7777.kazhdyidnevnik.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.cmix7777.kazhdyidnevnik.service.Backups
import io.github.cmix7777.kazhdyidnevnik.service.Updates
import io.github.cmix7777.kazhdyidnevnik.service.WeatherRepository
import java.util.concurrent.TimeUnit

/**
 * Фоновая проверка расписания: примерно раз в 3 часа и повтор, если в 23:00 или 6:30 не было сети.
 * Заодно проверяет обновления приложения и делает автокопию прогресса.
 */
class ScheduleCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ok = ScheduleSync.checkAll(applicationContext)
        // Попутно: новая версия приложения (не чаще раза в 12 часов) и ежедневная автокопия.
        runCatching { Updates.checkInBackground(applicationContext) }
        runCatching { Backups.saveAutoIfDue(applicationContext) }
        runCatching { WeatherRepository.fetch(applicationContext) }
        return when {
            ok -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "schedule-periodic"
        private const val NOW = "schedule-now"
        private const val MAX_ATTEMPTS = 3

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleCheckWorker>(3, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleCheckWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
