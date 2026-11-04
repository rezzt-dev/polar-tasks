package app.polar.ui.widget

import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import app.polar.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR

/**
 * Colores de un tema que necesitan sus vistas previas (selector de tema, fila de ajustes y editor
 * del tema personalizado). Se resuelven de un contexto con el tema aplicado, no del tema activo.
 */
data class ThemePreviewColors(
    val background: Int,
    val surface: Int,
    val surfaceContainerHighest: Int,
    val outlineVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val priorityHigh: Int,
    val priorityMedium: Int,
    val priorityLow: Int
) {
    /** Colores intermedios hacia [target] (`fraction` de 0 a 1), para animar el cambio de tema. */
    fun blend(target: ThemePreviewColors, fraction: Float): ThemePreviewColors {
        fun mix(from: Int, to: Int) = ColorUtils.blendARGB(from, to, fraction)
        return ThemePreviewColors(
            background = mix(background, target.background),
            surface = mix(surface, target.surface),
            surfaceContainerHighest = mix(surfaceContainerHighest, target.surfaceContainerHighest),
            outlineVariant = mix(outlineVariant, target.outlineVariant),
            primary = mix(primary, target.primary),
            onPrimary = mix(onPrimary, target.onPrimary),
            primaryContainer = mix(primaryContainer, target.primaryContainer),
            onPrimaryContainer = mix(onPrimaryContainer, target.onPrimaryContainer),
            onSurface = mix(onSurface, target.onSurface),
            onSurfaceVariant = mix(onSurfaceVariant, target.onSurfaceVariant),
            priorityHigh = mix(priorityHigh, target.priorityHigh),
            priorityMedium = mix(priorityMedium, target.priorityMedium),
            priorityLow = mix(priorityLow, target.priorityLow)
        )
    }

    companion object {
        fun resolve(themed: Context): ThemePreviewColors {
            fun color(@AttrRes attr: Int) = MaterialColors.getColor(themed, attr, Color.GRAY)
            return ThemePreviewColors(
                background = color(android.R.attr.colorBackground),
                surface = color(MaterialR.attr.colorSurface),
                surfaceContainerHighest = color(MaterialR.attr.colorSurfaceContainerHighest),
                outlineVariant = color(MaterialR.attr.colorOutlineVariant),
                primary = color(AppCompatR.attr.colorPrimary),
                onPrimary = color(MaterialR.attr.colorOnPrimary),
                primaryContainer = color(MaterialR.attr.colorPrimaryContainer),
                onPrimaryContainer = color(MaterialR.attr.colorOnPrimaryContainer),
                onSurface = color(MaterialR.attr.colorOnSurface),
                onSurfaceVariant = color(MaterialR.attr.colorOnSurfaceVariant),
                priorityHigh = color(R.attr.colorPriorityHigh),
                priorityMedium = color(R.attr.colorPriorityMedium),
                priorityLow = color(R.attr.colorPriorityLow)
            )
        }
    }
}
