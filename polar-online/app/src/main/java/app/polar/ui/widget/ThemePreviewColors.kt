package app.polar.ui.widget

import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import app.polar.R
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.ThemeManager
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
    /** Color con el que se ve un hueco del tema personalizado. */
    fun colorOf(slot: CustomThemePalette.Slot): Int = when (slot) {
        CustomThemePalette.Slot.BACKGROUND -> background
        CustomThemePalette.Slot.ACCENT -> primary
        CustomThemePalette.Slot.PRIORITY_HIGH -> priorityHigh
        CustomThemePalette.Slot.PRIORITY_MEDIUM -> priorityMedium
        CustomThemePalette.Slot.PRIORITY_LOW -> priorityLow
    }

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
        /** Colores de un tema (por clave) sea cual sea el activo; el personalizado necesita su combinacion. */
        fun forTheme(context: Context, key: String, customTheme: CustomTheme?): ThemePreviewColors =
            if (key == ThemeManager.THEME_CUSTOM && customTheme != null) {
                forCustomTheme(context, customTheme)
            } else {
                resolve(ContextThemeWrapper(context, ThemeManager.themeOption(key).previewStyleRes))
            }

        /**
         * Colores de una combinacion del tema personalizado. Los huecos de la paleta salen de sus
         * estilos; los colores a medida se calculan aqui (los mismos que carga
         * `CustomColorResources` en la app), sin tocar los recursos, para poder repintar la vista
         * previa en cada movimiento del selector de color.
         */
        fun forCustomTheme(context: Context, theme: CustomTheme): ThemePreviewColors {
            val resolved = resolve(CustomThemePalette.themedContext(context, theme))
            val custom = CustomThemePalette.colorOverrides(theme)
            if (custom.isEmpty()) return resolved
            fun pick(colorRes: Int, fallback: Int) = custom[colorRes] ?: fallback
            return ThemePreviewColors(
                background = pick(R.color.custom_hex_bg_background, resolved.background),
                surface = pick(R.color.custom_hex_bg_surface, resolved.surface),
                surfaceContainerHighest = pick(R.color.custom_hex_bg_surface_container_highest, resolved.surfaceContainerHighest),
                outlineVariant = pick(R.color.custom_hex_bg_outline_variant, resolved.outlineVariant),
                primary = pick(R.color.custom_hex_accent_primary, resolved.primary),
                onPrimary = pick(R.color.custom_hex_accent_on_primary, resolved.onPrimary),
                primaryContainer = pick(R.color.custom_hex_accent_container, resolved.primaryContainer),
                onPrimaryContainer = pick(R.color.custom_hex_accent_on_container, resolved.onPrimaryContainer),
                onSurface = pick(R.color.custom_hex_bg_foreground, resolved.onSurface),
                onSurfaceVariant = pick(R.color.custom_hex_bg_foreground_variant, resolved.onSurfaceVariant),
                priorityHigh = pick(R.color.custom_hex_priority_high, resolved.priorityHigh),
                priorityMedium = pick(R.color.custom_hex_priority_medium, resolved.priorityMedium),
                priorityLow = pick(R.color.custom_hex_priority_low, resolved.priorityLow)
            )
        }

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
