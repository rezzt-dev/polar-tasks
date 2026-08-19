package app.polar.ui.activity

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import app.polar.util.ThemeManager

abstract class BaseActivity : AppCompatActivity() {
    protected lateinit var themeManager: ThemeManager

    override fun attachBaseContext(newBase: Context) {
        val themeManager = ThemeManager(newBase)
        val fontScale = themeManager.loadFontScale()
        
        val config = newBase.resources.configuration
        config.fontScale = fontScale
        
        val sharedPrefs = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val localeStr = sharedPrefs.getString("app_locale", "es") ?: "es"
        val locale = if (localeStr.contains("-r")) {
            val parts = localeStr.split("-r")
            java.util.Locale(parts[0], parts[1])
        } else {
            java.util.Locale(localeStr)
        }
        java.util.Locale.setDefault(locale)
        config.setLocale(locale)
        
        val configContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(configContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        
        // Apply explicit theme if selected (must be before super.onCreate)
        val themeRes = themeManager.getThemeStyleRes()
        if (themeRes != 0) {
            setTheme(themeRes)
        }
        
        // Apply font overlay on top of any theme
        theme.applyStyle(themeManager.getFontOverlayStyle(), true)
        
        // Apply checkbox overlay
        val checkboxOverlay = themeManager.getCheckboxOverlayStyle()
        if (checkboxOverlay != 0) {
            theme.applyStyle(checkboxOverlay, true)
        }
        
        super.onCreate(savedInstanceState)

        // Ajustar iconos de status/navigation bar tan pronto como la ventana esté
        // disponible, para evitar parpadeos al arrancar.
        setLightStatusBar(themeManager.isLightTheme())
    }
    
    override fun onResume() {
        super.onResume()
        // Re-aplicar por si el modo noche cambió mientras la activity estaba pausada.
        setLightStatusBar(themeManager.isLightTheme())
    }
    
    private fun setLightStatusBar(light: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appearance = if (light) {
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else {
                    0
                }
                window.insetsController?.setSystemBarsAppearance(
                    appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                val decorView = window.decorView
                val flags = decorView.systemUiVisibility
                decorView.systemUiVisibility = if (light) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
        } catch (e: Exception) {
            // Ignorar: el ajuste de iconos de status bar no es crítico.
            e.printStackTrace()
        }
    }
}
