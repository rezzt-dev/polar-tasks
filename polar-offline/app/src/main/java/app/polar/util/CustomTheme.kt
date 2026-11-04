package app.polar.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import app.polar.R

/**
 * Combinacion elegida para el tema personalizado: un fondo, un color de acento y un color por
 * prioridad. Guarda solo claves de [CustomThemePalette]; los colores reales salen de los estilos
 * de `themes_custom.xml`, de modo que cada eleccion tiene tono claro y oscuro con buen contraste.
 */
data class CustomTheme(
    val background: String,
    val accent: String,
    val priorityHigh: String,
    val priorityMedium: String,
    val priorityLow: String
) {

    val isDark: Boolean get() = CustomThemePalette.background(background).isDark

    fun priorityColor(priority: CustomThemePalette.Priority): String = when (priority) {
        CustomThemePalette.Priority.HIGH -> priorityHigh
        CustomThemePalette.Priority.MEDIUM -> priorityMedium
        CustomThemePalette.Priority.LOW -> priorityLow
    }

    fun withPriorityColor(priority: CustomThemePalette.Priority, key: String): CustomTheme = when (priority) {
        CustomThemePalette.Priority.HIGH -> copy(priorityHigh = key)
        CustomThemePalette.Priority.MEDIUM -> copy(priorityMedium = key)
        CustomThemePalette.Priority.LOW -> copy(priorityLow = key)
    }

    /** Forma guardada en preferencias: `fondo,acento,alta,media,baja`. */
    fun serialize(): String = listOf(background, accent, priorityHigh, priorityMedium, priorityLow)
        .joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ","

        /**
         * Lee una combinacion guardada; `null` si falta o esta mal formada. Las claves que ya no
         * existan en la paleta se sustituyen por las de la combinacion por defecto de su modo.
         */
        fun parse(value: String?): CustomTheme? {
            val parts = value?.split(SEPARATOR)?.map { it.trim() } ?: return null
            if (parts.size != 5 || parts.any { it.isEmpty() }) return null
            return CustomThemePalette.sanitize(CustomTheme(parts[0], parts[1], parts[2], parts[3], parts[4]))
        }
    }
}

/**
 * Opciones del tema personalizado y los estilos que las aplican. El orden de cada lista es el
 * orden en que aparecen en el editor. Las claves se guardan en preferencias: no las renombres.
 * Los estilos y colores se generan con `.docs/agent-docs/tools/custom_theme_palette.py`.
 */
object CustomThemePalette {

    enum class Priority { HIGH, MEDIUM, LOW }

    data class Background(
        val key: String,
        @StringRes val labelRes: Int,
        @StyleRes val styleRes: Int,
        val isDark: Boolean
    )

    data class Accent(
        val key: String,
        @StringRes val labelRes: Int,
        @StyleRes private val lightOverlay: Int,
        @StyleRes private val darkOverlay: Int
    ) {
        @StyleRes fun overlay(dark: Boolean): Int = if (dark) darkOverlay else lightOverlay
    }

    /** Un color de prioridad: un overlay por prioridad y modo (alta, media, baja x claro, oscuro). */
    class PriorityColor(
        val key: String,
        @StringRes val labelRes: Int,
        high: Pair<Int, Int>,
        medium: Pair<Int, Int>,
        low: Pair<Int, Int>
    ) {
        private val overlays = mapOf(Priority.HIGH to high, Priority.MEDIUM to medium, Priority.LOW to low)

        @StyleRes fun overlay(priority: Priority, dark: Boolean): Int =
            overlays.getValue(priority).let { if (dark) it.second else it.first }
    }

    val BACKGROUNDS = listOf(
        Background("snow", R.string.custom_bg_snow, R.style.Theme_Polar_Custom_Snow, false),
        Background("ivory", R.string.custom_bg_ivory, R.style.Theme_Polar_Custom_Ivory, false),
        Background("mist", R.string.custom_bg_mist, R.style.Theme_Polar_Custom_Mist, false),
        Background("mint", R.string.custom_bg_mint, R.style.Theme_Polar_Custom_Mint, false),
        Background("petal", R.string.custom_bg_petal, R.style.Theme_Polar_Custom_Petal, false),
        Background("lavender", R.string.custom_bg_lavender, R.style.Theme_Polar_Custom_Lavender, false),
        Background("graphite", R.string.custom_bg_graphite, R.style.Theme_Polar_Custom_Graphite, true),
        Background("black", R.string.custom_bg_black, R.style.Theme_Polar_Custom_Black, true),
        Background("midnight", R.string.custom_bg_midnight, R.style.Theme_Polar_Custom_Midnight, true),
        Background("pine", R.string.custom_bg_pine, R.style.Theme_Polar_Custom_Pine, true),
        Background("plum", R.string.custom_bg_plum, R.style.Theme_Polar_Custom_Plum, true),
        Background("cocoa", R.string.custom_bg_cocoa, R.style.Theme_Polar_Custom_Cocoa, true)
    )

    val ACCENTS = listOf(
        Accent("ink", R.string.palette_ink, R.style.ThemeOverlay_Polar_Custom_Accent_Ink_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Ink_Dark),
        Accent("blue", R.string.palette_blue, R.style.ThemeOverlay_Polar_Custom_Accent_Blue_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Blue_Dark),
        Accent("indigo", R.string.palette_indigo, R.style.ThemeOverlay_Polar_Custom_Accent_Indigo_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Indigo_Dark),
        Accent("violet", R.string.palette_violet, R.style.ThemeOverlay_Polar_Custom_Accent_Violet_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Violet_Dark),
        Accent("pink", R.string.palette_pink, R.style.ThemeOverlay_Polar_Custom_Accent_Pink_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Pink_Dark),
        Accent("red", R.string.palette_red, R.style.ThemeOverlay_Polar_Custom_Accent_Red_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Red_Dark),
        Accent("orange", R.string.palette_orange, R.style.ThemeOverlay_Polar_Custom_Accent_Orange_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Orange_Dark),
        Accent("amber", R.string.palette_amber, R.style.ThemeOverlay_Polar_Custom_Accent_Amber_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Amber_Dark),
        Accent("green", R.string.palette_green, R.style.ThemeOverlay_Polar_Custom_Accent_Green_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Green_Dark),
        Accent("teal", R.string.palette_teal, R.style.ThemeOverlay_Polar_Custom_Accent_Teal_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Teal_Dark),
        Accent("cyan", R.string.palette_cyan, R.style.ThemeOverlay_Polar_Custom_Accent_Cyan_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Cyan_Dark),
        Accent("slate", R.string.palette_slate, R.style.ThemeOverlay_Polar_Custom_Accent_Slate_Light, R.style.ThemeOverlay_Polar_Custom_Accent_Slate_Dark)
    )

    val PRIORITY_COLORS = listOf(
        PriorityColor(
            "red", R.string.palette_red,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Red_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Red_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Red_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Red_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Red_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Red_Dark
        ),
        PriorityColor(
            "orange", R.string.palette_orange,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Orange_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Orange_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Orange_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Orange_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Orange_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Orange_Dark
        ),
        PriorityColor(
            "amber", R.string.palette_amber,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Amber_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Amber_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Amber_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Amber_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Amber_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Amber_Dark
        ),
        PriorityColor(
            "green", R.string.palette_green,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Green_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Green_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Green_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Green_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Green_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Green_Dark
        ),
        PriorityColor(
            "teal", R.string.palette_teal,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Teal_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Teal_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Teal_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Teal_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Teal_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Teal_Dark
        ),
        PriorityColor(
            "blue", R.string.palette_blue,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Blue_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Blue_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Blue_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Blue_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Blue_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Blue_Dark
        ),
        PriorityColor(
            "indigo", R.string.palette_indigo,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Indigo_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Indigo_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Indigo_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Indigo_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Indigo_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Indigo_Dark
        ),
        PriorityColor(
            "violet", R.string.palette_violet,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Violet_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Violet_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Violet_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Violet_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Violet_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Violet_Dark
        ),
        PriorityColor(
            "pink", R.string.palette_pink,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Pink_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Pink_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Pink_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Pink_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Pink_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Pink_Dark
        ),
        PriorityColor(
            "slate", R.string.palette_slate,
            R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Slate_Light to R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Slate_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Slate_Light to R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Slate_Dark,
            R.style.ThemeOverlay_Polar_Custom_PriorityLow_Slate_Light to R.style.ThemeOverlay_Polar_Custom_PriorityLow_Slate_Dark
        )
    )

    /** Punto de partida en fondo claro: los colores de prioridad de siempre con acento azul. */
    val DEFAULT_LIGHT = CustomTheme("snow", "blue", "red", "amber", "blue")

    /** Punto de partida en fondo oscuro. */
    val DEFAULT_DARK = CustomTheme("graphite", "blue", "red", "amber", "blue")

    fun defaultFor(dark: Boolean): CustomTheme = if (dark) DEFAULT_DARK else DEFAULT_LIGHT

    fun background(key: String): Background = BACKGROUNDS.find { it.key == key } ?: BACKGROUNDS.first()

    fun accent(key: String): Accent = ACCENTS.find { it.key == key } ?: ACCENTS.first { it.key == DEFAULT_LIGHT.accent }

    fun priorityColor(key: String): PriorityColor = PRIORITY_COLORS.find { it.key == key } ?: PRIORITY_COLORS.first()

    /** Sustituye las claves desconocidas por las de la combinacion por defecto de su modo. */
    fun sanitize(theme: CustomTheme): CustomTheme {
        val background = BACKGROUNDS.find { it.key == theme.background }
        val fallback = defaultFor(background?.isDark ?: false)
        fun accentOr(key: String, default: String) = if (ACCENTS.any { it.key == key }) key else default
        fun priorityOr(key: String, default: String) = if (PRIORITY_COLORS.any { it.key == key }) key else default
        return CustomTheme(
            background = background?.key ?: fallback.background,
            accent = accentOr(theme.accent, fallback.accent),
            priorityHigh = priorityOr(theme.priorityHigh, fallback.priorityHigh),
            priorityMedium = priorityOr(theme.priorityMedium, fallback.priorityMedium),
            priorityLow = priorityOr(theme.priorityLow, fallback.priorityLow)
        )
    }

    /** Tema completo del fondo, que se aplica con `setTheme()`. */
    @StyleRes fun baseStyle(theme: CustomTheme): Int = background(theme.background).styleRes

    /** Overlays de acento y prioridades, en el tono del modo del fondo, para aplicar sobre [baseStyle]. */
    fun overlayStyles(theme: CustomTheme): List<Int> {
        val dark = theme.isDark
        return listOf(accent(theme.accent).overlay(dark)) + Priority.entries.map { priority ->
            priorityColor(theme.priorityColor(priority)).overlay(priority, dark)
        }
    }

    /** Contexto con la combinacion aplicada, para resolver sus colores en vistas previas. */
    fun themedContext(context: Context, theme: CustomTheme): Context =
        ContextThemeWrapper(context, baseStyle(theme)).apply {
            overlayStyles(theme).forEach { this.theme.applyStyle(it, true) }
        }
}
