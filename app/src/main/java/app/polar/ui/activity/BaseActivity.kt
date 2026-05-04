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
        
        // Apply multicolor theme if selected (must be before super.onCreate)
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
    }
}
