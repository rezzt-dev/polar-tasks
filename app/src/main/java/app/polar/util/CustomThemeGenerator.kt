package app.polar.util

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Genera una paleta Material3 coherente a partir de un color de fondo base.
 *
 * La generación prioriza la armonía cromática: todos los acentos (primary,
 * secondary y sus contenedores) derivan del matiz del fondo. No se generan
 * colores aleatorios ni azules incongruentes.
 *
 * El modo claro/oscuro determina si las capas de superficie se aclaran u
 * oscurecen respecto al fondo, permitiendo temas profesionales y limpios.
 */
object CustomThemeGenerator {

  private const val DEFAULT_BACKGROUND = "#1A1A1A"
  private const val DEFAULT_FOREGROUND = "#FFFFFF"

  /**
   * Genera la paleta completa.
   *
   * @param backgroundHex Color de fondo base (#RRGGBB).
   * @param foregroundHex Color de primer plano opcional. Si es nulo o inválido
   *                      se calcula automáticamente para máximo contraste.
   * @param isDarkMode true para superficies más oscuras que el fondo (modo
   *                   oscuro); false para superficies más claras (modo claro).
   */
  fun generate(backgroundHex: String?, foregroundHex: String?, isDarkMode: Boolean): CustomThemeColors {
    val bg = parseHex(backgroundHex) ?: parseHex(DEFAULT_BACKGROUND)!!
    val fg = parseHex(foregroundHex) ?: autoForeground(bg)

    // --- Surface layers (variaciones del fondo) -----------------------------
    // Modo oscuro: capas ligeramente más claras para distinguir elevación.
    // Modo claro: capas ligeramente más oscuras para distinguir elevación.
    val surface = if (isDarkMode) lighten(bg, 0.08f) else darken(bg, 0.06f)
    val surfaceVariant = if (isDarkMode) lighten(bg, 0.04f) else darken(bg, 0.03f)
    val surfaceContainer = if (isDarkMode) lighten(bg, 0.12f) else darken(bg, 0.09f)
    val surfaceContainerHigh = if (isDarkMode) lighten(bg, 0.18f) else darken(bg, 0.13f)

    // --- Accents (derivados del matiz del fondo) ----------------------------
    val primary = derivePrimary(bg, fg, isDarkMode)
    val onPrimary = contrastColor(primary, fg)
    val primaryContainer = blend(primary, bg, if (isDarkMode) 0.18f else 0.14f)
    val onPrimaryContainer = contrastColor(primaryContainer, fg)

    val secondary = deriveSecondary(primary, bg, isDarkMode)
    val onSecondary = contrastColor(secondary, fg)
    val secondaryContainer = blend(secondary, bg, if (isDarkMode) 0.18f else 0.14f)
    val onSecondaryContainer = contrastColor(secondaryContainer, fg)

    // --- Outlines / borders --------------------------------------------------
    val outline = blend(fg, bg, if (isDarkMode) 0.18f else 0.22f)
    val outlineVariant = blend(fg, bg, if (isDarkMode) 0.10f else 0.14f)

    // --- Semantic colors (profesionales y coherentes) -----------------------
    val error = if (isDarkMode) Color.parseColor("#EF5350") else Color.parseColor("#E53935")
    val success = if (isDarkMode) Color.parseColor("#66BB6A") else Color.parseColor("#43A047")

    return CustomThemeColors(
      background = bg,
      foreground = fg,
      surface = surface,
      onSurface = fg,
      surfaceVariant = surfaceVariant,
      surfaceContainer = surfaceContainer,
      surfaceContainerHigh = surfaceContainerHigh,
      primary = primary,
      onPrimary = onPrimary,
      primaryContainer = primaryContainer,
      onPrimaryContainer = onPrimaryContainer,
      secondary = secondary,
      onSecondary = onSecondary,
      secondaryContainer = secondaryContainer,
      onSecondaryContainer = onSecondaryContainer,
      outline = outline,
      outlineVariant = outlineVariant,
      error = error,
      success = success,
      taskBackground = surface,
      taskBorder = outline,
      mainBorder = outline,
      buttonBackground = primary,
      isDark = isDarkMode
    )
  }

  /**
   * Sobrecarga de compatibilidad. Determina el modo a partir de la luminancia
   * del fondo cuando no hay preferencia guardada.
   */
  fun generate(backgroundHex: String?, foregroundHex: String?): CustomThemeColors {
    val bg = parseHex(backgroundHex) ?: parseHex(DEFAULT_BACKGROUND)!!
    val isDarkMode = relativeLuminance(bg) < 0.5
    return generate(backgroundHex, foregroundHex, isDarkMode)
  }

  /**
   * Devuelve blanco o negro según cuál contraste mejor con el fondo.
   */
  fun autoForeground(background: Int): Int {
    return if (relativeLuminance(background) < 0.5) Color.WHITE else Color.BLACK
  }

  /**
   * Deriva el color primario del matiz del fondo. Si el fondo es neutro, usa
   * el matiz del foreground cuando tenga cromatismo; en último caso genera un
   * gris profesional con un leve tinte frío.
   */
  private fun derivePrimary(background: Int, foreground: Int, isDark: Boolean): Int {
    val bgHsl = FloatArray(3)
    colorToHsl(background, bgHsl)

    return if (bgHsl[1] > 0.08f) {
      // El fondo aporta un matiz: lo usamos como acento.
      val saturation = if (isDark) 0.60f else 0.50f
      val lightness = if (isDark) 0.68f else 0.46f
      hslToColor(floatArrayOf(bgHsl[0], saturation, lightness))
    } else {
      // Fondo neutro: intentamos usar el foreground como semilla.
      val fgHsl = FloatArray(3)
      colorToHsl(foreground, fgHsl)
      if (fgHsl[1] > 0.10f) {
        val saturation = if (isDark) 0.55f else 0.45f
        val lightness = if (isDark) 0.65f else 0.45f
        hslToColor(floatArrayOf(fgHsl[0], saturation, lightness))
      } else {
        // Gris profesional con un tinte frío muy sutil.
        val saturation = 0.06f
        val lightness = if (isDark) 0.70f else 0.38f
        hslToColor(floatArrayOf(210f, saturation, lightness))
      }
    }
  }

  /**
   * Deriva el color secundario como una variante más suave del primario,
   * manteniendo el mismo matiz para mantener la armonía.
   */
  private fun deriveSecondary(primary: Int, background: Int, isDark: Boolean): Int {
    val hsl = FloatArray(3)
    colorToHsl(primary, hsl)

    val saturation = (hsl[1] * 0.55f).coerceIn(0.04f, 0.45f)
    val lightness = if (isDark) {
      (hsl[2] * 0.82f).coerceIn(0.25f, 0.55f)
    } else {
      (hsl[2] * 1.12f).coerceIn(0.35f, 0.65f)
    }
    return hslToColor(floatArrayOf(hsl[0], saturation, lightness))
  }

  // -------------------------------------------------------------------------
  // Parsing / formatting
  // -------------------------------------------------------------------------

  fun parseHex(hex: String?): Int? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().replace("#", "")
    return try {
      when (cleaned.length) {
        3 -> {
          val r = cleaned[0].toString().repeat(2)
          val g = cleaned[1].toString().repeat(2)
          val b = cleaned[2].toString().repeat(2)
          Color.parseColor("#$r$g$b")
        }
        6 -> Color.parseColor("#$cleaned")
        8 -> {
          // AARRGGBB: convertimos a ARGB int directamente
          val argb = cleaned.toLong(16).toInt()
          Color.argb(
            Color.alpha(argb),
            Color.red(argb),
            Color.green(argb),
            Color.blue(argb)
          )
        }
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }

  fun toHex(color: Int): String {
    return String.format("#%06X", 0xFFFFFF and color)
  }

  /**
   * Calcula la luminancia relativa WCAG 2.1 de un color.
   */
  fun relativeLuminance(color: Int): Double {
    fun channel(c: Int): Double {
      val s = c / 255.0
      return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(Color.red(color)) +
        0.7152 * channel(Color.green(color)) +
        0.0722 * channel(Color.blue(color))
  }

  /**
   * Ratio de contraste entre dos colores según WCAG.
   */
  fun contrastRatio(a: Int, b: Int): Double {
    val l1 = relativeLuminance(a) + 0.05
    val l2 = relativeLuminance(b) + 0.05
    return max(l1, l2) / min(l1, l2)
  }

  // -------------------------------------------------------------------------
  // HSL helpers
  // -------------------------------------------------------------------------

  fun colorToHsl(color: Int, hsl: FloatArray) {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f

    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min

    var h = 0f
    var s = 0f
    val l = (max + min) / 2f

    if (delta != 0f) {
      s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
      when (max) {
        r -> h = ((g - b) / delta + (if (g < b) 6 else 0)) % 6
        g -> h = (b - r) / delta + 2
        b -> h = (r - g) / delta + 4
      }
      h *= 60f
    }

    hsl[0] = h
    hsl[1] = s
    hsl[2] = l
  }

  fun hslToColor(hsl: FloatArray): Int {
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r, g, b) = when {
      h < 60 -> Triple(c, x, 0f)
      h < 120 -> Triple(x, c, 0f)
      h < 180 -> Triple(0f, c, x)
      h < 240 -> Triple(0f, x, c)
      h < 300 -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }

    return Color.rgb(
      ((r + m) * 255).roundToInt(),
      ((g + m) * 255).roundToInt(),
      ((b + m) * 255).roundToInt()
    )
  }

  // -------------------------------------------------------------------------
  // Color manipulation
  // -------------------------------------------------------------------------

  fun lighten(color: Int, amount: Float): Int {
    return blend(Color.WHITE, color, amount.coerceIn(0f, 1f))
  }

  fun darken(color: Int, amount: Float): Int {
    return blend(Color.BLACK, color, amount.coerceIn(0f, 1f))
  }

  fun shiftHue(color: Int, degrees: Float): Int {
    val hsl = FloatArray(3)
    colorToHsl(color, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    if (hsl[0] < 0) hsl[0] += 360f
    return hslToColor(hsl)
  }

  /**
   * Mezcla `color` sobre `background` con la proporción dada.
   * ratio = 0 → background, ratio = 1 → color.
   */
  fun blend(color: Int, background: Int, ratio: Float): Int {
    val r = (Color.red(color) * ratio + Color.red(background) * (1 - ratio)).roundToInt()
    val g = (Color.green(color) * ratio + Color.green(background) * (1 - ratio)).roundToInt()
    val b = (Color.blue(color) * ratio + Color.blue(background) * (1 - ratio)).roundToInt()
    return Color.rgb(r, g, b)
  }

  /**
   * Devuelve blanco, negro o el color fallback según cuál contraste mejor con
   * el color dado.
   */
  fun contrastColor(color: Int, fallback: Int = Color.BLACK): Int {
    val blackContrast = contrastRatio(color, Color.BLACK)
    val whiteContrast = contrastRatio(color, Color.WHITE)
    val fallbackContrast = contrastRatio(color, fallback)
    return when {
      whiteContrast >= blackContrast && whiteContrast >= fallbackContrast -> Color.WHITE
      blackContrast >= fallbackContrast -> Color.BLACK
      else -> fallback
    }
  }

  private fun clamp(value: Float, min: Float, max: Float): Float {
    return max(min, min(max, value))
  }

}
