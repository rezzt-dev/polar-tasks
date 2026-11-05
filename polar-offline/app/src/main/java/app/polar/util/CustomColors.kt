package app.polar.util

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Operaciones de color para los colores a medida del tema personalizado: lectura y formato de
 * codigos hex, mezcla y contraste (wcag 2.1). Trabajan con enteros ARGB en Kotlin puro (sin
 * `android.graphics.Color`) para poder probarse en la JVM, y replican las funciones de
 * `.docs/agent-docs/tools/custom_theme_palette.py`.
 */
object CustomColors {

    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val BLACK: Int = 0xFF000000.toInt()

    /** Numero de digitos de un codigo hex (`RRGGBB`). */
    const val HEX_LENGTH = 6

    private val HEX_PATTERN = Regex("#?([0-9a-fA-F]{$HEX_LENGTH})")

    /** Lee `#RRGGBB` o `RRGGBB` (sin distinguir mayusculas); `null` si no es un codigo valido. */
    fun parse(value: String?): Int? {
        val digits = value?.trim()?.let { HEX_PATTERN.matchEntire(it) }?.groupValues?.get(1) ?: return null
        return digits.toInt(16) or BLACK
    }

    /** Codigo `#RRGGBB` en mayusculas (se ignora la transparencia). */
    fun format(color: Int): String = "#%06X".format(Locale.ROOT, color and 0xFFFFFF)

    /** Mezcla [from] hacia [to] en proporcion [fraction] (0 = [from], 1 = [to]); resultado opaco. */
    fun mix(from: Int, to: Int, fraction: Double): Int {
        val t = fraction.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        }
        return BLACK or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Luminancia relativa wcag (0 negro, 1 blanco). */
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((color shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Relacion de contraste wcag entre dos colores opacos (de 1 a 21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Un fondo es oscuro si el texto blanco se lee mejor sobre el que el negro. */
    fun isDark(background: Int): Boolean = contrast(background, WHITE) > contrast(background, BLACK)

    /** Blanco o negro, el que mas contraste tenga sobre [background]. */
    fun readableOn(background: Int): Int = if (isDark(background)) WHITE else BLACK

    /**
     * Acerca [color] a [toward] lo justo para que tenga al menos [minimum] de contraste con todos
     * los colores de [against]. Si ni siquiera [toward] llega al minimo, devuelve [toward].
     */
    fun ensureContrast(color: Int, against: List<Int>, minimum: Double, toward: Int): Int {
        fun enough(candidate: Int) = against.all { contrast(candidate, it) >= minimum }
        if (enough(color)) return color
        for (step in 1..CONTRAST_STEPS) {
            val candidate = mix(color, toward, step.toDouble() / CONTRAST_STEPS)
            if (enough(candidate)) return candidate
        }
        return toward
    }

    fun ensureContrast(color: Int, against: Int, minimum: Double, toward: Int): Int =
        ensureContrast(color, listOf(against), minimum, toward)

    private const val CONTRAST_STEPS = 40
}
