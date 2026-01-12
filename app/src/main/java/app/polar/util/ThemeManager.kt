package app.polar.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(private val context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  
  companion object {
    private const val PREFS_NAME = "polar_prefs"
    private const val KEY_THEME = "theme"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"
  }
  
  fun saveTheme(theme: String) {
    prefs.edit().putString(KEY_THEME, theme).apply()
    applyTheme(theme)
  }
  
  fun loadTheme(): String {
    return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
  }
  
  fun applyTheme(theme: String) {
    when (theme) {
      THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
      THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
      THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
  }
  
  fun toggleTheme() {
    val currentTheme = loadTheme()
    val newTheme = when (currentTheme) {
      THEME_LIGHT -> THEME_DARK
      THEME_DARK -> THEME_LIGHT
      else -> THEME_DARK // Default to dark if system
    }
    saveTheme(newTheme)
  }

  fun isLightTheme(): Boolean {
    val currentTheme = loadTheme()
    return currentTheme == THEME_LIGHT ||
        (currentTheme == THEME_SYSTEM && !isSystemInDarkMode())
  }

  private fun isSystemInDarkMode(): Boolean {
    val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
  }
}
