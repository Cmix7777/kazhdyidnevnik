package io.github.cmix7777.kazhdyidnevnik.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import io.github.cmix7777.kazhdyidnevnik.R
import io.github.cmix7777.kazhdyidnevnik.data.ChatLogic
import io.github.cmix7777.kazhdyidnevnik.data.ChatMessage
import io.github.cmix7777.kazhdyidnevnik.data.Person
import io.github.cmix7777.kazhdyidnevnik.service.ChatSync
import io.github.cmix7777.kazhdyidnevnik.ui.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.app.Person as NotificationPerson

/** Уведомление о новых сообщениях: переписка целиком, ответ прямо из шторки и «Прочитано». */
object ChatNotifier {

    const val CHANNEL = "messages"
    const val KEY_REPLY = "reply"
    private const val ID = 6000
    private const val ACCENT_AIZAT = 0xFFA855F7.toInt()
    private const val ACCENT_NASTYA = 0xFFA77BEA.toInt()

    fun channel(): NotificationChannel =
        NotificationChannel(CHANNEL, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Новые сообщения в переписке на двоих"
        }

    @SuppressLint("MissingPermission")
    fun show(context: Context, owner: Person, messages: List<ChatMessage>) {
        if (messages.isEmpty() || !Notifier.canPost(context)) return
        val partner = owner.partner
        val me = NotificationPerson.Builder().setName("Я").setKey(owner.id).build()
        val them = NotificationPerson.Builder().setName(partner.shortName).setKey(partner.id).build()
        val style = NotificationCompat.MessagingStyle(me)
        messages.sortedBy { it.at }.takeLast(8).forEach { style.addMessage(ChatLogic.shortText(it), it.at, them) }
        val last = messages.maxBy { it.at }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(if (owner == Person.NASTYA) ACCENT_NASTYA else ACCENT_AIZAT)
            .setStyle(style)
            .setContentTitle(partner.shortName)
            .setContentText(ChatLogic.shortText(last))
            .setNumber(messages.size)
            .setWhen(last.at)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openChat(context))
            .setAutoCancel(true)
            .addAction(replyAction(context))
            .addAction(readAction(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (e: SecurityException) {
            // Разрешение на уведомления забрали между проверкой и показом.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    /** Открыть переписку (назад — в дневник). */
    fun openChat(context: Context): PendingIntent {
        val intent = Intent(context, ChatActivity::class.java)
        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(7, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            ?: PendingIntent.getActivity(
                context,
                7,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }

    private fun replyAction(context: Context): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY).setLabel("Ответить").build()
        val intent = Intent(context, ChatActionReceiver::class.java).setAction(ChatActionReceiver.ACTION_REPLY)
        // Для ответа из шторки система дописывает текст в intent, поэтому он изменяемый (адресат указан явно).
        val pending = PendingIntent.getBroadcast(context, 61, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, "Ответить", pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun readAction(context: Context): NotificationCompat.Action {
        val intent = Intent(context, ChatActionReceiver::class.java).setAction(ChatActionReceiver.ACTION_READ)
        val pending = PendingIntent.getBroadcast(context, 62, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, "Прочитано", pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }
}

/** Кнопки уведомления о сообщениях: ответ из шторки и «Прочитано». */
class ChatActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatNotifier.KEY_REPLY)?.toString()?.trim()
                if (text.isNullOrEmpty()) return
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ChatSync.replyFromNotification(app, text)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_READ -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ChatSync.readFromNotification(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "io.github.cmix7777.kazhdyidnevnik.CHAT_REPLY"
        const val ACTION_READ = "io.github.cmix7777.kazhdyidnevnik.CHAT_READ"
    }
}
