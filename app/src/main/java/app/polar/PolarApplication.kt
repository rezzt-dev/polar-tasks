package app.polar

import android.app.Application
import app.polar.util.NotificationHelper

class PolarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ThemeManager to apply saved theme preference immediately
        val themeManager = app.polar.util.ThemeManager(this)
        themeManager.applyTheme(themeManager.loadTheme())
        
        NotificationHelper.createNotificationChannel(this)
    }
}
