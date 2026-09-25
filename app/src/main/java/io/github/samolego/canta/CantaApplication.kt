package io.github.samolego.canta

import android.app.Application
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.ops.CanaServices

class CantaApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        // Initializing the SettingsStore
        SettingsStore.initialize(applicationContext)
        CanaServices.initialize(applicationContext)
    }
}
