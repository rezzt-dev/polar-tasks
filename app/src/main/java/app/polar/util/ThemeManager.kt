package app.polar.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import app.polar.R

class ThemeManager(private val context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  
  companion object {
    private const val PREFS_NAME = "polar_prefs"
    private const val KEY_THEME = "theme"
    private const val KEY_FONT = "font"
    private const val KEY_CHECKBOX_STYLE = "checkbox_style"
    
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_MULTICOLOR_LIGHT = "multicolor_light"
    const val THEME_MULTICOLOR_DARK = "multicolor_dark"
    const val THEME_PASTEL = "pastel"
    const val THEME_NEON = "neon"
    const val THEME_SYSTEM = "system"
    const val THEME_CUSTOM = "custom"
    
    private const val KEY_CUSTOM_BACKGROUND = "custom_background"
    private const val KEY_CUSTOM_FOREGROUND = "custom_foreground"
    private const val KEY_CUSTOM_IS_DARK_MODE = "custom_is_dark_mode"
    
    const val FONT_POPPINS = "poppins"
    const val FONT_COMFORTAA = "comfortaa"
    const val FONT_FIGTREE = "figtree"
    const val FONT_JETBRAINS_MONO = "jetbrains_mono"
    const val FONT_ARIAL = "arial"
    const val FONT_SYSTEM = "system"
    
    const val CHECKBOX_SQUARE = "square"
    const val CHECKBOX_CIRCULAR = "circular"
    
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
  
  fun saveCheckboxStyle(style: String) {
      prefs.edit().putString(KEY_CHECKBOX_STYLE, style).apply()
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
  
  fun loadCheckboxStyle(): String {
      return prefs.getString(KEY_CHECKBOX_STYLE, CHECKBOX_SQUARE) ?: CHECKBOX_SQUARE
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

  fun getCheckboxOverlayStyle(): Int {
      return when (loadCheckboxStyle()) {
          CHECKBOX_CIRCULAR -> R.style.Overlay_Polar_Checkbox_Circular
          else -> 0 // 0 means no overlay, default square behavior
      }
  }

  fun getThemeStyleRes(): Int {
      return when (loadTheme()) {
          THEME_MULTICOLOR_LIGHT -> R.style.Theme_Polar_MulticolorLight
          THEME_MULTICOLOR_DARK -> R.style.Theme_Polar_MulticolorDark
          THEME_PASTEL -> R.style.Theme_Polar_Pastel
          THEME_NEON -> R.style.Theme_Polar_Neon
          THEME_CUSTOM -> R.style.Theme_Polar_Custom
          else -> 0 // Default light/dark handled by DayNight
      }
  }
  
  fun saveCustomThemeColors(backgroundHex: String, foregroundHex: String, isDarkMode: Boolean) {
    prefs.edit()
      .putString(KEY_CUSTOM_BACKGROUND, backgroundHex)
      .putString(KEY_CUSTOM_FOREGROUND, foregroundHex)
      .putBoolean(KEY_CUSTOM_IS_DARK_MODE, isDarkMode)
      .apply()
  }
  
  fun loadCustomBackground(): String {
    return prefs.getString(KEY_CUSTOM_BACKGROUND, CustomThemeColors.DEFAULT_BACKGROUND)
      ?: CustomThemeColors.DEFAULT_BACKGROUND
  }
  
  fun loadCustomForeground(): String {
    return prefs.getString(KEY_CUSTOM_FOREGROUND, CustomThemeColors.DEFAULT_FOREGROUND)
      ?: CustomThemeColors.DEFAULT_FOREGROUND
  }
  
  fun loadCustomThemeColors(): CustomThemeColors {
    val background = loadCustomBackground()
    val foreground = loadCustomForeground()
    val isDarkMode = loadCustomIsDarkMode()
    return CustomThemeGenerator.generate(background, foreground, isDarkMode)
  }

  fun saveCustomIsDarkMode(isDarkMode: Boolean) {
    prefs.edit().putBoolean(KEY_CUSTOM_IS_DARK_MODE, isDarkMode).apply()
  }

  fun loadCustomIsDarkMode(): Boolean {
    // Fallback: si no existe la preferencia, inferir del fondo guardado para no
    // romper los temas creados antes de este cambio.
    if (!prefs.contains(KEY_CUSTOM_IS_DARK_MODE)) {
      val bg = CustomThemeGenerator.parseHex(loadCustomBackground()) ?: Color.BLACK
      return CustomThemeGenerator.relativeLuminance(bg) < 0.5
    }
    return prefs.getBoolean(KEY_CUSTOM_IS_DARK_MODE, true)
  }
  
  fun hasCustomThemeColors(): Boolean {
    return prefs.contains(KEY_CUSTOM_BACKGROUND) && prefs.contains(KEY_CUSTOM_FOREGROUND)
  }
  
  fun applyTheme(theme: String) {
    when (theme) {
      THEME_LIGHT, THEME_MULTICOLOR_LIGHT, THEME_PASTEL -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
      THEME_DARK, THEME_MULTICOLOR_DARK, THEME_NEON -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
      THEME_CUSTOM -> {
        val colors = loadCustomThemeColors()
        val mode = if (colors.isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
      }
      THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
    if (currentTheme == THEME_CUSTOM) return !loadCustomThemeColors().isDark
    return currentTheme == THEME_LIGHT || currentTheme == THEME_MULTICOLOR_LIGHT || currentTheme == THEME_PASTEL ||
        (currentTheme == THEME_SYSTEM && !isSystemInDarkMode())
  }

  private fun isSystemInDarkMode(): Boolean {
    val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
  }
}
