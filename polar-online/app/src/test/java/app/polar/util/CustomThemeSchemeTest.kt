package app.polar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemeSchemeTest {

    /** Muestra de todo el espectro: grises, tonos saturados y apagados, claros y oscuros. */
    private val samples: List<Int> = buildList {
        for (gray in 0..255 step 17) add(CustomColors.BLACK or (gray shl 16) or (gray shl 8) or gray)
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            add(CustomColors.BLACK or (r shl 16) or (g shl 8) or b)
        }
    }

    @Test
    fun background_keepsTheChosenColorAndReadableText() {
        samples.forEach { color ->
            val bg = CustomThemeScheme.background(color)
            val hex = CustomColors.format(color)
            assertEquals(hex, color, bg.background)
            assertEquals(hex, CustomColors.isDark(color), bg.isDark)
            // Siempre se puede llegar a 4.5:1 con blanco o negro; 7:1 salvo en los tonos medios.
            val text = CustomColors.contrast(bg.foreground, bg.background)
            assertTrue("$hex texto $text", text >= CustomThemeScheme.MIN_ON_ACCENT)
            val best = maxOf(CustomColors.contrast(color, CustomColors.WHITE), CustomColors.contrast(color, CustomColors.BLACK))
            if (best >= CustomThemeScheme.MIN_TEXT) assertTrue("$hex texto $text", text >= CustomThemeScheme.MIN_TEXT)
            // El texto secundario no se lee peor que el principal.
            assertTrue(CustomColors.contrast(bg.foregroundVariant, bg.background) <= text + 0.01)
        }
    }

    @Test
    fun background_derivesSurfacesInTheSameDirectionAsThePalette() {
        val light = CustomThemeScheme.background(0xFFF5F0E6.toInt())
        assertFalse(light.isDark)
        assertTrue(CustomColors.luminance(light.surface) >= CustomColors.luminance(light.background))
        assertTrue(CustomColors.luminance(light.surfaceContainerHighest) < CustomColors.luminance(light.background))
        assertTrue(CustomColors.contrast(light.foregroundVariant, light.surface) >= CustomThemeScheme.MIN_TEXT_VARIANT)

        val dark = CustomThemeScheme.background(0xFF1E1E2E.toInt())
        assertTrue(dark.isDark)
        assertTrue(CustomColors.luminance(dark.surfaceContainerHighest) > CustomColors.luminance(dark.background))
        assertTrue(CustomColors.contrast(dark.foregroundVariant, dark.surface) >= CustomThemeScheme.MIN_TEXT_VARIANT)
    }

    @Test
    fun accent_keepsTheChosenColorAndReadableRoles() {
        samples.forEach { color ->
            listOf(false, true).forEach { dark ->
                val accent = CustomThemeScheme.accent(color, dark)
                val label = "${CustomColors.format(color)} oscuro=$dark"
                assertEquals(label, color, accent.primary)
                // El texto sobre el acento es el mejor de los dos candidatos cuando ninguno llega al minimo.
                val onPrimary = CustomColors.contrast(accent.onPrimary, accent.primary)
                val bestCandidate = maxOf(
                    CustomColors.contrast(CustomColors.WHITE, color),
                    CustomColors.contrast(CustomColors.mix(color, CustomColors.BLACK, 0.85), color)
                )
                assertTrue("$label sobre acento $onPrimary", onPrimary >= minOf(CustomThemeScheme.MIN_ON_ACCENT, bestCandidate) - 0.01)
                assertTrue(label, CustomColors.contrast(accent.onContainer, accent.container) >= CustomThemeScheme.MIN_ON_ACCENT)
            }
        }
    }
}
