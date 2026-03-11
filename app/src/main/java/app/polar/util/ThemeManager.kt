package app.polar.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import app.polar.R

class ThemeManager(private val context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  
  companion object {
    private const val PREFS_NAME = "polar_prefs"
    private const val KEY_THEME = "theme"
    private const val KEY_FONT = "font"
    private const val KEY_LANGUAGE = "language"
    
    // Theme Constants
    
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_MULTICOLOR_LIGHT = "multicolor_light"
    const val THEME_MULTICOLOR_DARK = "multicolor_dark"
    const val THEME_PASTEL = "pastel"
    const val THEME_NEON = "neon"
    const val THEME_SYSTEM = "system"
    
    const val FONT_POPPINS = "poppins"
    const val FONT_COMFORTAA = "comfortaa"
    const val FONT_FIGTREE = "figtree"
    const val FONT_JETBRAINS_MONO = "jetbrains_mono"
    const val FONT_ARIAL = "arial"
    const val FONT_SYSTEM = "system"
    
    // Language Constants
    const val LANG_SYSTEM = "system"
    const val LANG_ES = "es"
    const val LANG_EN = "en"
    const val LANG_FR = "fr"
    const val LANG_DE = "de"
    
    private const val KEY_FONT_SCALE = "font_scale"
    const val DEFAULT_FONT_SCALE = 1.0f
  }
  
  fun saveTheme(theme: String) {
    prefs.edit().putString(KEY_THEME, theme).apply()
    applyTheme(theme)
  }
  
  fun saveFont(font: String) {
      prefs.edit().putString(KEY_FONT, font).apply()
  }
  
  fun saveLanguage(language: String) {
      prefs.edit().putString(KEY_LANGUAGE, language).apply()
      applyLanguage(language)
  }
  
  fun saveFontScale(scale: Float) {
      prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
  }
  
  fun loadTheme(): String {
    return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
  }
  
  fun loadFont(): String {
      return prefs.getString(KEY_FONT, FONT_POPPINS) ?: FONT_POPPINS
  }
  
  fun loadLanguage(): String {
      return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
  }
  
  fun loadFontScale(): Float {
      return prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
  }
  
  fun getFontOverlayStyle(): Int {
      return when (loadFont()) {
          FONT_POPPINS -> R.style.Overlay_Polar_Font_Poppins
          FONT_COMFORTAA -> R.style.Overlay_Polar_Font_Comfortaa
          FONT_FIGTREE -> R.style.Overlay_Polar_Font_Figtree
          FONT_JETBRAINS_MONO -> R.style.Overlay_Polar_Font_JetBrainsMono
          FONT_ARIAL -> R.style.Overlay_Polar_Font_Arial
          FONT_SYSTEM -> R.style.Overlay_Polar_Font_System
          else -> R.style.Overlay_Polar_Font_Poppins
      }
  }

  fun getThemeStyleRes(): Int {
      return when (loadTheme()) {
          THEME_MULTICOLOR_LIGHT -> R.style.Theme_Polar_MulticolorLight
          THEME_MULTICOLOR_DARK -> R.style.Theme_Polar_MulticolorDark
          THEME_PASTEL -> R.style.Theme_Polar_Pastel
          THEME_NEON -> R.style.Theme_Polar_Neon
          else -> 0 // Default light/dark handled by DayNight
      }
  }
  
  fun applyTheme(theme: String) {
    when (theme) {
      THEME_LIGHT, THEME_MULTICOLOR_LIGHT, THEME_PASTEL -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
      THEME_DARK, THEME_MULTICOLOR_DARK, THEME_NEON -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
      THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
  }
  
  fun applyLanguage(language: String) {
      if (language == LANG_SYSTEM) {
          AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
      } else {
          val appLocale: androidx.core.os.LocaleListCompat = androidx.core.os.LocaleListCompat.forLanguageTags(language)
          AppCompatDelegate.setApplicationLocales(appLocale)
      }
  }
  
  fun toggleTheme() {
    val currentTheme = loadTheme()
    val newTheme = when (currentTheme) {
      THEME_LIGHT -> THEME_DARK
      THEME_DARK -> THEME_LIGHT
      else -> THEME_DARK
    }
    saveTheme(newTheme)
  }

  fun isLightTheme(): Boolean {
    val currentTheme = loadTheme()
    return currentTheme == THEME_LIGHT || currentTheme == THEME_MULTICOLOR_LIGHT || currentTheme == THEME_PASTEL ||
        (currentTheme == THEME_SYSTEM && !isSystemInDarkMode())
  }

  private fun isSystemInDarkMode(): Boolean {
    val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
  }
}
