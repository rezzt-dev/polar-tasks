package app.polar

import android.app.Application
import app.polar.util.NotificationHelper

class PolarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }
}
