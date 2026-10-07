package io.github.cmix7777.kazhdyidnevnik

import android.app.Application
import io.github.cmix7777.kazhdyidnevnik.data.ProgressRepository
import io.github.cmix7777.kazhdyidnevnik.notify.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ProgressRepository.init(filesDir)
    }
}
