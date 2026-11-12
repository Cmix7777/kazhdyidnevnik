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
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import java.util.concurrent.TimeUnit

private val network = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

/**
 * Проверка новых сообщений, когда приложение закрыто: примерно раз в 15 минут (чаще Android без
 * своего сервера не разрешает). С мгновенными уведомлениями ([io.github.cmix7777.kazhdyidnevnik.service.ChatPush])
 * она лишь подстраховка.
 */
class ChatCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { ChatSync.backgroundCheck(applicationContext) }
        // Токена для мгновенных уведомлений ещё нет (не пустил VPN или сеть): пробуем снова.
        runCatching {
            if (AppSettings(applicationContext).pushToken == null) io.github.cmix7777.kazhdyidnevnik.service.ChatPush.init(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "chat-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChatCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Досылка сообщений, которые не ушли сразу (не было сети): ждёт сеть и пробует с растущей паузой. */
class ChatSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sent = runCatching { ChatSync.upload(applicationContext) }.getOrDefault(false)
        runCatching { ChatSync.flush() }
        return when {
            sent -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "chat-send"
        private const val MAX_ATTEMPTS = 10

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatSendWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/**
 * Сообщение «в будущее»: будит телефон к назначенному времени (без сети не ждёт — сообщение встанет
 * в очередь как обычное и уйдёт, когда сеть появится). Android может разбудить с опозданием на несколько минут.
 */
class ScheduledMessageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { ChatSync.releaseDue(applicationContext) }
        runCatching { ChatSync.flush() }
        return Result.success()
    }

    companion object {
        private fun name(id: String) = "chat-later-$id"

        fun enqueue(context: Context, id: String, at: Long) {
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, id: String) {
            WorkManager.getInstance(context).cancelUniqueWork(name(id))
        }
    }
}
