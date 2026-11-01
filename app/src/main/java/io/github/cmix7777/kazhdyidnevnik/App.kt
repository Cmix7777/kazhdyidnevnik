package io.github.cmix7777.kazhdyidnevnik

import android.app.Application
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.notify.ChatCheckWorker
import io.github.cmix7777.kazhdyidnevnik.notify.Notifier
import io.github.cmix7777.kazhdyidnevnik.service.ChatPush

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ProgressRepository.init(filesDir)
        // Мгновенные уведомления о сообщениях (если настроены) и запасная проверка раз в 15 минут.
        ChatPush.init(this)
        runCatching { ChatCheckWorker.schedule(this) }
    }
}
