package app.polar.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import app.polar.R

class ThemeManager(private val context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  
  /**
   * Temas que se pueden elegir, en el orden en que aparecen en el selector.
   * `previewStyleRes` es el estilo explicito del tema (tambien para claro/oscuro, que en la app
   * se resuelven con DayNight) para pintar su vista previa sea cual sea el tema activo.
   */
  data class ThemeOption(
    val key: String,
    val labelRes: Int,
    val previewStyleRes: Int,
    val isDark: Boolean
  )

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
    const val THEME_ONYX = "onyx"
    const val THEME_SAND = "sand"
    const val THEME_SAGE = "sage"
    const val THEME_BLOSSOM = "blossom"
    const val THEME_FOREST = "forest"
    const val THEME_AMETHYST = "amethyst"
    const val THEME_EMBER = "ember"
    const val THEME_SYSTEM = "system"
    
    const val FONT_INTER = "inter"
    const val FONT_POPPINS = "poppins"
    const val FONT_COMFORTAA = "comfortaa"
    const val FONT_FIGTREE = "figtree"
    const val FONT_JETBRAINS_MONO = "jetbrains_mono"
    const val FONT_ARIAL = "arial"
    const val FONT_SYSTEM = "system"
    
    const val CHECKBOX_SQUARE = "square"
    const val CHECKBOX_CIRCULAR = "circular"
    
    val THEME_OPTIONS = listOf(
      ThemeOption(THEME_LIGHT, R.string.theme_light, R.style.Base_Theme_Polar, false),
      ThemeOption(THEME_MULTICOLOR_LIGHT, R.string.theme_multicolor_light, R.style.Theme_Polar_MulticolorLight, false),
      ThemeOption(THEME_PASTEL, R.string.theme_pastel, R.style.Theme_Polar_Pastel, false),
      ThemeOption(THEME_SAND, R.string.theme_sand, R.style.Theme_Polar_Sand, false),
      ThemeOption(THEME_SAGE, R.string.theme_sage, R.style.Theme_Polar_Sage, false),
      ThemeOption(THEME_BLOSSOM, R.string.theme_blossom, R.style.Theme_Polar_Blossom, false),
      ThemeOption(THEME_DARK, R.string.theme_dark, R.style.Base_Theme_Polar_Dark, true),
      ThemeOption(THEME_ONYX, R.string.theme_onyx, R.style.Theme_Polar_Onyx, true),
      ThemeOption(THEME_MULTICOLOR_DARK, R.string.theme_multicolor_dark, R.style.Theme_Polar_MulticolorDark, true),
      ThemeOption(THEME_NEON, R.string.theme_neon, R.style.Theme_Polar_Neon, true),
      ThemeOption(THEME_FOREST, R.string.theme_forest, R.style.Theme_Polar_Forest, true),
      ThemeOption(THEME_AMETHYST, R.string.theme_amethyst, R.style.Theme_Polar_Amethyst, true),
      ThemeOption(THEME_EMBER, R.string.theme_ember, R.style.Theme_Polar_Ember, true)
    )

    /** Opcion del tema guardado; el oscuro si la clave no existe. */
    fun themeOption(key: String): ThemeOption =
      THEME_OPTIONS.find { it.key == key } ?: THEME_OPTIONS.first { it.key == THEME_DARK }

    private const val KEY_FONT_SCALE = "font_scale"
    const val DEFAULT_FONT_SCALE = 1.0f

    /** Idioma elegido en ajustes ("es", "en-rGB", "de"...); "es" si no se ha elegido ninguno. */
    fun loadAppLocale(context: Context): java.util.Locale {
      val localeStr = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("app_locale", "es") ?: "es"
      // "en-rGB" (formato de recursos) -> etiqueta BCP 47 "en-GB"
      return java.util.Locale.forLanguageTag(localeStr.replace("-r", "-"))
    }

    /**
     * Contexto con el idioma de la app para textos que se generan fuera de una activity
     * (notificaciones, widget): el contexto de aplicación usa el idioma del sistema.
     */
    fun localizedContext(context: Context): Context {
      val config = android.content.res.Configuration(context.resources.configuration)
      config.setLocale(loadAppLocale(context))
      return context.createConfigurationContext(config)
    }
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
    val saved = prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    // Migracion: el tema personalizado ya no existe; se fuerza al tema oscuro.
    return if (saved == "custom") THEME_DARK else saved
  }
  
  fun loadFont(): String {
      return prefs.getString(KEY_FONT, FONT_INTER) ?: FONT_INTER
  }
  
  fun loadCheckboxStyle(): String {
      return prefs.getString(KEY_CHECKBOX_STYLE, CHECKBOX_SQUARE) ?: CHECKBOX_SQUARE
  }
  
  fun loadFontScale(): Float {
      return prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
  }
  
  fun getFontOverlayStyle(): Int {
      return when (loadFont()) {
          FONT_INTER -> R.style.Overlay_Polar_Font_Inter
          FONT_POPPINS -> R.style.Overlay_Polar_Font_Poppins
          FONT_COMFORTAA -> R.style.Overlay_Polar_Font_Comfortaa
          FONT_FIGTREE -> R.style.Overlay_Polar_Font_Figtree
          FONT_JETBRAINS_MONO -> R.style.Overlay_Polar_Font_JetBrainsMono
          FONT_ARIAL -> R.style.Overlay_Polar_Font_Arial
          FONT_SYSTEM -> R.style.Overlay_Polar_Font_System
          else -> R.style.Overlay_Polar_Font_Inter
      }
  }

  fun getCheckboxOverlayStyle(): Int {
      return when (loadCheckboxStyle()) {
          CHECKBOX_CIRCULAR -> R.style.Overlay_Polar_Checkbox_Circular
          else -> 0 // 0 means no overlay, default square behavior
      }
  }

  fun getThemeStyleRes(): Int {
      return when (val theme = loadTheme()) {
          THEME_LIGHT, THEME_DARK, THEME_SYSTEM -> 0 // Default light/dark handled by DayNight
          else -> themeOption(theme).previewStyleRes
      }
  }
  
  fun applyTheme(theme: String) {
    val mode = when {
      theme == THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
      themeOption(theme).isDark -> AppCompatDelegate.MODE_NIGHT_YES
      else -> AppCompatDelegate.MODE_NIGHT_NO
    }
    AppCompatDelegate.setDefaultNightMode(mode)
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
    return if (currentTheme == THEME_SYSTEM) !isSystemInDarkMode() else !themeOption(currentTheme).isDark
  }

  private fun isSystemInDarkMode(): Boolean {
    val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
  }
}
