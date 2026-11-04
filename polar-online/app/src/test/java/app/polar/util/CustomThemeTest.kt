package app.polar.util

import android.content.Context
import app.polar.util.CustomThemePalette.Priority
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemeTest {

    @Test
    fun serialize_roundTrips() {
        val theme = CustomTheme("midnight", "violet", "pink", "orange", "teal")
        assertEquals(theme, CustomTheme.parse(theme.serialize()))
    }

    @Test
    fun parse_rejectsMissingOrMalformedValues() {
        assertNull(CustomTheme.parse(null))
        assertNull(CustomTheme.parse(""))
        assertNull(CustomTheme.parse("snow,blue,red"))
        assertNull(CustomTheme.parse("snow,blue,red,amber,blue,extra"))
        assertNull(CustomTheme.parse("snow,,red,amber,blue"))
    }

    @Test
    fun parse_replacesUnknownKeysWithTheDefaultsOfTheirMode() {
        val parsed = CustomTheme.parse("pine,gold,red,amber,neon")
        assertEquals(
            CustomTheme("pine", CustomThemePalette.DEFAULT_DARK.accent, "red", "amber", CustomThemePalette.DEFAULT_DARK.priorityLow),
            parsed
        )
        // Fondo desconocido: combinacion clara por defecto, conservando lo que si existe.
        assertEquals("snow", CustomTheme.parse("sepia,green,red,amber,blue")?.background)
        assertEquals("green", CustomTheme.parse("sepia,green,red,amber,blue")?.accent)
    }

    @Test
    fun isDark_followsTheBackground() {
        assertFalse(CustomThemePalette.DEFAULT_LIGHT.isDark)
        assertTrue(CustomThemePalette.DEFAULT_DARK.isDark)
        assertTrue(CustomThemePalette.DEFAULT_LIGHT.copy(background = "black").isDark)
        assertFalse(CustomThemePalette.DEFAULT_DARK.copy(background = "ivory").isDark)
    }

    @Test
    fun priorityColor_readsAndWritesEachPriority() {
        var theme = CustomThemePalette.DEFAULT_LIGHT
        theme = theme.withPriorityColor(Priority.HIGH, "pink")
            .withPriorityColor(Priority.MEDIUM, "violet")
            .withPriorityColor(Priority.LOW, "slate")
        assertEquals("pink", theme.priorityColor(Priority.HIGH))
        assertEquals("violet", theme.priorityColor(Priority.MEDIUM))
        assertEquals("slate", theme.priorityColor(Priority.LOW))
        assertEquals(CustomThemePalette.DEFAULT_LIGHT.accent, theme.accent)
    }

    @Test
    fun overlayStyles_useTheToneOfTheBackgroundMode() {
        val light = CustomTheme("mint", "teal", "red", "amber", "blue")
        val dark = light.copy(background = "pine")

        assertEquals(
            listOf(
                CustomThemePalette.accent("teal").overlay(dark = false),
                CustomThemePalette.priorityColor("red").overlay(Priority.HIGH, dark = false),
                CustomThemePalette.priorityColor("amber").overlay(Priority.MEDIUM, dark = false),
                CustomThemePalette.priorityColor("blue").overlay(Priority.LOW, dark = false)
            ),
            CustomThemePalette.overlayStyles(light)
        )
        assertEquals(CustomThemePalette.accent("teal").overlay(dark = true), CustomThemePalette.overlayStyles(dark).first())
        assertNotEquals(CustomThemePalette.overlayStyles(light), CustomThemePalette.overlayStyles(dark))
        assertEquals(CustomThemePalette.background("pine").styleRes, CustomThemePalette.baseStyle(dark))
    }

    @Test
    fun palette_keysAreUniqueAndDefaultsAreValid() {
        listOf(
            CustomThemePalette.BACKGROUNDS.map { it.key },
            CustomThemePalette.ACCENTS.map { it.key },
            CustomThemePalette.PRIORITY_COLORS.map { it.key }
        ).forEach { keys -> assertEquals(keys.size, keys.toSet().size) }

        listOf(CustomThemePalette.DEFAULT_LIGHT, CustomThemePalette.DEFAULT_DARK).forEach { default ->
            assertEquals(default, CustomThemePalette.sanitize(default))
        }
        // Ambos modos tienen fondos para elegir.
        assertTrue(CustomThemePalette.BACKGROUNDS.any { it.isDark })
        assertTrue(CustomThemePalette.BACKGROUNDS.any { !it.isDark })
    }

    @Test
    fun themeManager_appliesTheCustomThemeOnlyWhenItIsActiveAndSaved() {
        val prefs = FakeSharedPreferences()
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        val manager = ThemeManager(context)

        // Elegido pero sin combinacion guardada (preferencias incompletas): tema oscuro.
        manager.saveTheme(ThemeManager.THEME_CUSTOM, applyNightMode = false)
        assertEquals(ThemeManager.THEME_DARK, manager.loadTheme())
        assertTrue(manager.getColorOverlayStyles().isEmpty())

        val custom = CustomTheme("ivory", "green", "red", "amber", "blue")
        manager.saveCustomTheme(custom)
        assertEquals(ThemeManager.THEME_CUSTOM, manager.loadTheme())
        assertEquals(custom, manager.loadCustomTheme())
        assertEquals(CustomThemePalette.baseStyle(custom), manager.getThemeStyleRes())
        assertEquals(CustomThemePalette.overlayStyles(custom), manager.getColorOverlayStyles())
        assertTrue(manager.isLightTheme())

        // La firma cambia al editar la combinacion aunque el tema siga siendo el mismo.
        val before = manager.themeSignature()
        manager.saveCustomTheme(custom.copy(background = "black"))
        assertNotEquals(before, manager.themeSignature())
        assertFalse(manager.isLightTheme())

        // Con otro tema activo la combinacion se conserva pero no se aplica.
        manager.saveTheme(ThemeManager.THEME_SAND, applyNightMode = false)
        assertTrue(manager.getColorOverlayStyles().isEmpty())
        assertEquals(ThemeManager.THEME_SAND, manager.themeSignature())
        assertEquals("black", manager.loadCustomTheme()?.background)
    }
}
