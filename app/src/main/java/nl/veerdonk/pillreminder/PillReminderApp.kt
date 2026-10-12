package nl.veerdonk.pillreminder

import android.app.Application

class PillReminderApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
