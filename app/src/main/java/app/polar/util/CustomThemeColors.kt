package app.polar.util

/**
 * Paleta completa generada dinámicamente para el tema personalizado.
 * Contiene todos los colores Material3 y los atributos personalizados que
 * necesitan los layouts de Polar.
 */
data class CustomThemeColors(
  val background: Int,
  val foreground: Int,
  val surface: Int,
  val onSurface: Int,
  val surfaceVariant: Int,
  val surfaceContainer: Int,
  val surfaceContainerHigh: Int,
  val primary: Int,
  val onPrimary: Int,
  val primaryContainer: Int,
  val onPrimaryContainer: Int,
  val secondary: Int,
  val onSecondary: Int,
  val secondaryContainer: Int,
  val onSecondaryContainer: Int,
  val outline: Int,
  val outlineVariant: Int,
  val error: Int,
  val success: Int,
  val taskBackground: Int,
  val taskBorder: Int,
  val mainBorder: Int,
  val buttonBackground: Int,
  val isDark: Boolean
) {
  companion object {
    const val DEFAULT_BACKGROUND = "#1A1A1A"
    const val DEFAULT_FOREGROUND = "#FFFFFF"
  }
}
