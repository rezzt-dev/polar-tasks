package app.polar.ui.view

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat

/**
 * Fuente del tema para las gráficas dibujadas en Canvas, que no heredan el `fontFamily` del tema
 * como los TextView: así respetan la fuente elegida en ajustes (inter por defecto).
 */
internal object ChartTypeface {
    fun resolve(context: Context, style: Int = Typeface.NORMAL): Typeface {
        val value = TypedValue()
        val base = if (context.theme.resolveAttribute(android.R.attr.fontFamily, value, true)) {
            when {
                value.resourceId != 0 && value.type != TypedValue.TYPE_STRING ->
                    runCatching { ResourcesCompat.getFont(context, value.resourceId) }.getOrNull()
                value.string != null -> Typeface.create(value.string.toString(), Typeface.NORMAL)
                else -> null
            }
        } else {
            null
        }
        return Typeface.create(base ?: Typeface.DEFAULT, style)
    }
}
