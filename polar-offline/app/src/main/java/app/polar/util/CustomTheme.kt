package app.polar.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import app.polar.R

/**
 * Combinacion elegida para el tema personalizado: un fondo, un color de acento y un color por
 * prioridad. Cada valor es una clave de [CustomThemePalette] (sus colores salen de los estilos de
 * `themes_custom.xml`, con tono claro y oscuro) o un color a medida `#RRGGBB` (ver
 * [CustomColorResources]).
 */
data class CustomTheme(
    val background: String,
    val accent: String,
    val priorityHigh: String,
    val priorityMedium: String,
    val priorityLow: String
) {

    /** Un fondo a medida es oscuro o claro segun su luminosidad. */
    val isDark: Boolean
        get() = CustomThemePalette.customColor(background)?.let(CustomColors::isDark)
            ?: CustomThemePalette.background(background).isDark

    /** Tiene algun color a medida. */
    val hasCustomColors: Boolean
        get() = CustomThemePalette.Slot.entries.any { CustomThemePalette.customColor(get(it)) != null }

    operator fun get(slot: CustomThemePalette.Slot): String = when (slot) {
        CustomThemePalette.Slot.BACKGROUND -> background
        CustomThemePalette.Slot.ACCENT -> accent
        CustomThemePalette.Slot.PRIORITY_HIGH -> priorityHigh
        CustomThemePalette.Slot.PRIORITY_MEDIUM -> priorityMedium
        CustomThemePalette.Slot.PRIORITY_LOW -> priorityLow
    }

    fun with(slot: CustomThemePalette.Slot, value: String): CustomTheme = when (slot) {
        CustomThemePalette.Slot.BACKGROUND -> copy(background = value)
        CustomThemePalette.Slot.ACCENT -> copy(accent = value)
        CustomThemePalette.Slot.PRIORITY_HIGH -> copy(priorityHigh = value)
        CustomThemePalette.Slot.PRIORITY_MEDIUM -> copy(priorityMedium = value)
        CustomThemePalette.Slot.PRIORITY_LOW -> copy(priorityLow = value)
    }

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

    /** Forma guardada en preferencias: `fondo,acento,alta,media,baja` (claves o `#RRGGBB`). */
    fun serialize(): String = listOf(background, accent, priorityHigh, priorityMedium, priorityLow)
        .joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ","

        /**
         * Lee una combinacion guardada; `null` si falta o esta mal formada. Las claves que ya no
         * existan en la paleta (y los colores a medida si el dispositivo no los admite) se
         * sustituyen por los de la combinacion por defecto de su modo.
         */
        fun parse(value: String?, allowCustomColors: Boolean = CustomColorResources.isSupported): CustomTheme? {
            val parts = value?.split(SEPARATOR)?.map { it.trim() } ?: return null
            if (parts.size != 5 || parts.any { it.isEmpty() }) return null
            return CustomThemePalette.sanitize(CustomTheme(parts[0], parts[1], parts[2], parts[3], parts[4]), allowCustomColors)
        }
    }
}

/**
 * Opciones del tema personalizado y los estilos que las aplican. El orden de cada lista es el
 * orden en que aparecen en el editor. Las claves se guardan en preferencias: no las renombres.
 * Los estilos y colores se generan con `.docs/agent-docs/tools/custom_theme_palette.py`.
 *
 * Ademas de las claves, cada hueco admite un color a medida `#RRGGBB`: se pinta con los estilos
 * portadores (`*.Hex`) y sus tonos ([CustomThemeScheme]) se cargan con [CustomColorResources].
 */
object CustomThemePalette {

    enum class Priority { HIGH, MEDIUM, LOW }

    /** Cada eleccion de la combinacion: fondo, acento y un color por prioridad. */
    enum class Slot(val priority: Priority? = null) {
        BACKGROUND,
        ACCENT,
        PRIORITY_HIGH(Priority.HIGH),
        PRIORITY_MEDIUM(Priority.MEDIUM),
        PRIORITY_LOW(Priority.LOW);

        companion object {
            fun of(priority: Priority): Slot = entries.first { it.priority == priority }
        }
    }

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

    /** Color de un valor a medida (`#RRGGBB`); `null` si es una clave de la paleta. */
    fun customColor(value: String): Int? = if (value.startsWith("#")) CustomColors.parse(value) else null

    fun background(key: String): Background = BACKGROUNDS.find { it.key == key } ?: BACKGROUNDS.first()

    fun accent(key: String): Accent = ACCENTS.find { it.key == key } ?: ACCENTS.first { it.key == DEFAULT_LIGHT.accent }

    fun priorityColor(key: String): PriorityColor = PRIORITY_COLORS.find { it.key == key } ?: PRIORITY_COLORS.first()

    /**
     * Normaliza una combinacion: los colores a medida pasan a `#RRGGBB` en mayusculas y las claves
     * desconocidas, los codigos mal formados y (sin [allowCustomColors]) los colores a medida se
     * sustituyen por los de la combinacion por defecto de su modo.
     */
    fun sanitize(theme: CustomTheme, allowCustomColors: Boolean = CustomColorResources.isSupported): CustomTheme {
        val background = BACKGROUNDS.find { it.key == theme.background }
        val customBackground = customColor(theme.background)
        val fallback = defaultFor(customBackground?.let(CustomColors::isDark) ?: background?.isDark ?: false)
        fun valueOr(value: String, default: String, isKey: (String) -> Boolean): String {
            customColor(value)?.let { return if (allowCustomColors) CustomColors.format(it) else default }
            return if (isKey(value)) value else default
        }
        fun priorityOr(value: String, default: String) = valueOr(value, default) { key -> PRIORITY_COLORS.any { it.key == key } }
        return CustomTheme(
            background = valueOr(theme.background, fallback.background) { background != null },
            accent = valueOr(theme.accent, fallback.accent) { key -> ACCENTS.any { it.key == key } },
            priorityHigh = priorityOr(theme.priorityHigh, fallback.priorityHigh),
            priorityMedium = priorityOr(theme.priorityMedium, fallback.priorityMedium),
            priorityLow = priorityOr(theme.priorityLow, fallback.priorityLow)
        )
    }

    /** Tema completo del fondo, que se aplica con `setTheme()`. */
    @StyleRes fun baseStyle(theme: CustomTheme): Int {
        val custom = customColor(theme.background) ?: return background(theme.background).styleRes
        return if (CustomColors.isDark(custom)) R.style.Theme_Polar_Custom_Hex_Dark else R.style.Theme_Polar_Custom_Hex_Light
    }

    /** Overlays de acento y prioridades, en el tono del modo del fondo, para aplicar sobre [baseStyle]. */
    fun overlayStyles(theme: CustomTheme): List<Int> {
        val dark = theme.isDark
        val accentStyle = if (customColor(theme.accent) != null) {
            R.style.ThemeOverlay_Polar_Custom_Accent_Hex
        } else {
            accent(theme.accent).overlay(dark)
        }
        return listOf(accentStyle) + Priority.entries.map { priority ->
            val value = theme.priorityColor(priority)
            if (customColor(value) != null) customPriorityOverlay(priority) else priorityColor(value).overlay(priority, dark)
        }
    }

    /**
     * Valores de los colores portadores (`custom_hex_*`) para los huecos a medida de [theme]: id de
     * recurso -> color. Vacio si la combinacion solo usa la paleta.
     */
    fun colorOverrides(theme: CustomTheme): Map<Int, Int> = buildMap {
        customColor(theme.background)?.let { color ->
            val bg = CustomThemeScheme.background(color)
            put(R.color.custom_hex_bg_background, bg.background)
            put(R.color.custom_hex_bg_surface, bg.surface)
            put(R.color.custom_hex_bg_surface_container_lowest, bg.surfaceContainerLowest)
            put(R.color.custom_hex_bg_surface_container_low, bg.surfaceContainerLow)
            put(R.color.custom_hex_bg_surface_container, bg.surfaceContainer)
            put(R.color.custom_hex_bg_surface_container_high, bg.surfaceContainerHigh)
            put(R.color.custom_hex_bg_surface_container_highest, bg.surfaceContainerHighest)
            put(R.color.custom_hex_bg_surface_bright, bg.surfaceBright)
            put(R.color.custom_hex_bg_surface_dim, bg.surfaceDim)
            put(R.color.custom_hex_bg_foreground, bg.foreground)
            put(R.color.custom_hex_bg_foreground_variant, bg.foregroundVariant)
            put(R.color.custom_hex_bg_outline, bg.outline)
            put(R.color.custom_hex_bg_outline_variant, bg.outlineVariant)
            put(R.color.custom_hex_bg_inverse_surface, bg.inverseSurface)
            put(R.color.custom_hex_bg_inverse_on_surface, bg.inverseOnSurface)
        }
        customColor(theme.accent)?.let { color ->
            val accent = CustomThemeScheme.accent(color, theme.isDark)
            put(R.color.custom_hex_accent_primary, accent.primary)
            put(R.color.custom_hex_accent_on_primary, accent.onPrimary)
            put(R.color.custom_hex_accent_container, accent.container)
            put(R.color.custom_hex_accent_on_container, accent.onContainer)
            put(R.color.custom_hex_accent_primary_inverse, accent.primaryInverse)
        }
        Priority.entries.forEach { priority ->
            customColor(theme.priorityColor(priority))?.let { put(customPriorityColorRes(priority), it) }
        }
    }

    /** Nombre de la eleccion de un hueco para la interfaz: el de la paleta o el codigo `#RRGGBB`. */
    fun label(context: Context, slot: Slot, value: String): String {
        customColor(value)?.let { return CustomColors.format(it) }
        return context.getString(
            when (slot) {
                Slot.BACKGROUND -> background(value).labelRes
                Slot.ACCENT -> accent(value).labelRes
                else -> priorityColor(value).labelRes
            }
        )
    }

    @StyleRes private fun customPriorityOverlay(priority: Priority): Int = when (priority) {
        Priority.HIGH -> R.style.ThemeOverlay_Polar_Custom_PriorityHigh_Hex
        Priority.MEDIUM -> R.style.ThemeOverlay_Polar_Custom_PriorityMedium_Hex
        Priority.LOW -> R.style.ThemeOverlay_Polar_Custom_PriorityLow_Hex
    }

    private fun customPriorityColorRes(priority: Priority): Int = when (priority) {
        Priority.HIGH -> R.color.custom_hex_priority_high
        Priority.MEDIUM -> R.color.custom_hex_priority_medium
        Priority.LOW -> R.color.custom_hex_priority_low
    }

    /**
     * Contexto con los estilos de la combinacion aplicados. Los huecos a medida resuelven ahi sus
     * colores de respaldo: para vistas previas usa `ThemePreviewColors.forCustomTheme()`.
     */
    fun themedContext(context: Context, theme: CustomTheme): Context =
        ContextThemeWrapper(context, baseStyle(theme)).apply {
            overlayStyles(theme).forEach { this.theme.applyStyle(it, true) }
        }
}
