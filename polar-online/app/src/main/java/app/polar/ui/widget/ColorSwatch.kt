package app.polar.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

/**
 * Muestra de color circular de las paletas (color de lista, tema personalizado). La seleccionada
 * se marca con un anillo separado del color por un hueco del color de fondo.
 */
object ColorSwatch {

    /**
     * @param ringColor anillo de la muestra seleccionada (normalmente `colorOnSurface`).
     * @param gapColor hueco entre el anillo y el color (el fondo sobre el que se dibuja).
     * @param borderColor contorno fino para colores que se confunden con el fondo; `null` sin contorno.
     */
    fun drawable(
        context: Context,
        color: Int,
        selected: Boolean,
        ringColor: Int,
        gapColor: Int,
        borderColor: Int? = null
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            when {
                selected -> setStroke((3 * density).toInt(), gapColor)
                borderColor != null -> setStroke(density.toInt().coerceAtLeast(1), borderColor)
            }
        }
        if (!selected) return fill

        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * density).toInt(), ringColor)
        }
        val inset = (2 * density).toInt()
        return LayerDrawable(arrayOf(ring, fill)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    /**
     * Muestra para elegir un color a medida: un aro con todos los tonos alrededor de un hueco del
     * color de fondo, donde va el icono de la pipeta.
     */
    fun custom(context: Context, gapColor: Int): Drawable {
        val density = context.resources.displayMetrics.density
        val hues = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.SWEEP_GRADIENT
            colors = HUES
        }
        val gap = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(gapColor)
        }
        val inset = (3 * density).toInt()
        return LayerDrawable(arrayOf(hues, gap)).apply { setLayerInset(1, inset, inset, inset, inset) }
    }

    private val HUES = intArrayOf(
        Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
    )
}
