package app.polar.util

import app.polar.util.CustomColors.BLACK
import app.polar.util.CustomColors.WHITE
import app.polar.util.CustomColors.ensureContrast
import app.polar.util.CustomColors.mix

/**
 * Tonos que el tema personalizado deriva de un color a medida. Sigue los mismos pasos que
 * `background_palette()` y `accent_palette()` de `.docs/agent-docs/tools/custom_theme_palette.py`
 * (que genera los fondos y acentos predefinidos), de modo que un color a medida se integra igual
 * que uno de la paleta: superficies, contornos y textos salen del fondo, y el texto sobre el acento
 * se elige para que se lea. Si cambias una formula aqui, cambiala tambien en el script.
 */
object CustomThemeScheme {

    /** Minimos de contraste (wcag 2.1), los mismos que valida el script para la paleta. */
    const val MIN_TEXT = 7.0
    const val MIN_TEXT_VARIANT = 4.5
    const val MIN_ON_ACCENT = 4.5
    const val MIN_ACCENT = 3.0
    const val MIN_PRIORITY = 2.8

    /** Superficie inversa aproximada (snackbars) en cada modo, para el tono inverso del acento. */
    private const val INVERSE_SURFACE_ON_LIGHT = 0xFF2A2A2D.toInt()
    private const val INVERSE_SURFACE_ON_DARK = 0xFFF4F4F5.toInt()
    private const val DARK_CONTAINER_BASE = 0xFF18181B.toInt()

    data class Background(
        val isDark: Boolean,
        val background: Int,
        val surface: Int,
        val surfaceContainerLowest: Int,
        val surfaceContainerLow: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val surfaceContainerHighest: Int,
        val surfaceBright: Int,
        val surfaceDim: Int,
        val foreground: Int,
        val foregroundVariant: Int,
        val outline: Int,
        val outlineVariant: Int,
        val inverseSurface: Int,
        val inverseOnSurface: Int
    )

    data class Accent(
        val primary: Int,
        val onPrimary: Int,
        val container: Int,
        val onContainer: Int,
        /** Tono del acento sobre la superficie inversa (acciones de los snackbars). */
        val primaryInverse: Int
    )

    /** Roles de un fondo a medida: el modo (claro u oscuro) sale de su luminosidad. */
    fun background(color: Int): Background {
        val bg = color or BLACK
        val dark = CustomColors.isDark(bg)
        val extreme = if (dark) WHITE else BLACK
        // Texto casi negro o casi blanco con un matiz del fondo, como en los fondos de la paleta.
        val fg = ensureContrast(mix(bg, extreme, if (dark) 0.94 else 0.9), bg, MIN_TEXT, extreme)

        return if (dark) {
            val surface = mix(bg, fg, 0.03)
            val highest = mix(bg, fg, 0.125)
            Background(
                isDark = true,
                background = bg,
                surface = surface,
                surfaceContainerLowest = mix(bg, BLACK, 0.15),
                surfaceContainerLow = mix(bg, fg, 0.018),
                surfaceContainer = mix(bg, fg, 0.05),
                surfaceContainerHigh = mix(bg, fg, 0.085),
                surfaceContainerHighest = highest,
                surfaceBright = highest,
                surfaceDim = bg,
                foreground = fg,
                foregroundVariant = variant(mix(bg, fg, 0.6), bg, surface, fg),
                outline = mix(bg, fg, 0.225),
                outlineVariant = mix(bg, fg, 0.10),
                inverseSurface = mix(fg, bg, 0.04),
                inverseOnSurface = mix(bg, BLACK, 0.15)
            )
        } else {
            val surface = mix(bg, WHITE, 0.75)
            val highest = mix(bg, fg, 0.10)
            Background(
                isDark = false,
                background = bg,
                surface = surface,
                surfaceContainerLowest = surface,
                surfaceContainerLow = mix(bg, fg, 0.015),
                surfaceContainer = mix(bg, fg, 0.035),
                surfaceContainerHigh = mix(bg, fg, 0.065),
                surfaceContainerHighest = highest,
                surfaceBright = surface,
                surfaceDim = highest,
                foreground = fg,
                foregroundVariant = variant(mix(bg, fg, 0.64), bg, surface, fg),
                outline = mix(bg, fg, 0.16),
                outlineVariant = mix(bg, fg, 0.075),
                inverseSurface = mix(fg, bg, 0.08),
                inverseOnSurface = bg
            )
        }
    }

    /**
     * Roles de un acento a medida sobre un fondo [dark] o claro. El acento se respeta tal cual; el
     * texto encima, el contenedor y el tono inverso se ajustan para que se lean.
     */
    fun accent(color: Int, dark: Boolean): Accent {
        val primary = color or BLACK
        val light = WHITE
        val deep = mix(primary, BLACK, 0.85)
        // Cada modo tiene su texto preferido (como en la paleta); si no llega al minimo, el que mas contraste tenga.
        val preferred = if (dark) deep else light
        val other = if (dark) light else deep
        val onPrimary = when {
            CustomColors.contrast(preferred, primary) >= MIN_ON_ACCENT -> preferred
            CustomColors.contrast(other, primary) > CustomColors.contrast(preferred, primary) -> other
            else -> preferred
        }

        val container = if (dark) mix(DARK_CONTAINER_BASE, primary, 0.3) else mix(WHITE, primary, 0.16)
        val onContainer = if (dark) {
            ensureContrast(mix(primary, WHITE, 0.7), container, MIN_ON_ACCENT, WHITE)
        } else {
            ensureContrast(mix(primary, BLACK, 0.5), container, MIN_ON_ACCENT, BLACK)
        }
        val primaryInverse = if (dark) {
            ensureContrast(primary, INVERSE_SURFACE_ON_DARK, MIN_ACCENT, BLACK)
        } else {
            ensureContrast(primary, INVERSE_SURFACE_ON_LIGHT, MIN_ACCENT, WHITE)
        }
        return Accent(primary, onPrimary, container, onContainer, primaryInverse)
    }

    /** Texto secundario legible sobre el fondo y sobre las tarjetas. */
    private fun variant(color: Int, background: Int, surface: Int, foreground: Int): Int =
        ensureContrast(color, listOf(background, surface), MIN_TEXT_VARIANT, foreground)
}
