package app.polar.ui.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.polar.util.ThemeManager

abstract class BaseActivity : AppCompatActivity() {
    protected lateinit var themeManager: ThemeManager

    override fun attachBaseContext(newBase: Context) {
        val themeManager = ThemeManager(newBase)
        val fontScale = themeManager.loadFontScale()
        
        val config = newBase.resources.configuration
        config.fontScale = fontScale
        
        val newContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(newContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        
        // Theme logic is now properly initialized in Application class for Day/Night mode
        // but we still need to apply attributes if necessary, though AppCompatDelegate usually handles it.
        // Crucially, we MUST apply the Font Overlay style here.
        setTheme(themeManager.getFontOverlayStyle())
        
        super.onCreate(savedInstanceState)
    }
}
