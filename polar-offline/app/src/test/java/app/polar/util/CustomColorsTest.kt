package app.polar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorsTest {

    @Test
    fun parse_acceptsSixHexDigitsWithOrWithoutHash() {
        assertEquals(0xFF3A7BD5.toInt(), CustomColors.parse("#3A7BD5"))
        assertEquals(0xFF3A7BD5.toInt(), CustomColors.parse("3a7bd5"))
        assertEquals(0xFF3A7BD5.toInt(), CustomColors.parse("  #3a7BD5 "))
        listOf(null, "", "#", "#3A7BD", "#3A7BD55", "#3A7BG5", "3A 7BD5", "#FFF").forEach {
            assertNull("$it", CustomColors.parse(it))
        }
    }

    @Test
    fun format_isUppercaseAndIgnoresAlpha() {
        assertEquals("#3A7BD5", CustomColors.format(0x803a7bd5.toInt()))
        assertEquals("#000000", CustomColors.format(CustomColors.BLACK))
        assertEquals("#FFFFFF", CustomColors.format(CustomColors.WHITE))
    }

    @Test
    fun contrast_matchesTheWcagReferenceValues() {
        assertEquals(21.0, CustomColors.contrast(CustomColors.BLACK, CustomColors.WHITE), 0.01)
        assertEquals(1.0, CustomColors.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        // #767676 es el gris mas claro con 4.5:1 sobre blanco.
        assertEquals(4.54, CustomColors.contrast(0xFF767676.toInt(), CustomColors.WHITE), 0.01)
    }

    @Test
    fun mix_blendsEachChannel() {
        assertEquals(CustomColors.BLACK, CustomColors.mix(CustomColors.BLACK, CustomColors.WHITE, 0.0))
        assertEquals(CustomColors.WHITE, CustomColors.mix(CustomColors.BLACK, CustomColors.WHITE, 1.0))
        assertEquals("#808080", CustomColors.format(CustomColors.mix(CustomColors.BLACK, CustomColors.WHITE, 0.5)))
    }

    @Test
    fun isDark_picksTheModeWhereTextReadsBest() {
        assertTrue(CustomColors.isDark(0xFF1E1E2E.toInt()))
        assertTrue(CustomColors.isDark(0xFF2563EB.toInt()))
        assertFalse(CustomColors.isDark(0xFFFAFAFA.toInt()))
        assertFalse(CustomColors.isDark(0xFF808080.toInt()))
        assertEquals(CustomColors.WHITE, CustomColors.readableOn(0xFF1E1E2E.toInt()))
        assertEquals(CustomColors.BLACK, CustomColors.readableOn(0xFFFAB387.toInt()))
    }

    @Test
    fun ensureContrast_movesOnlyAsFarAsNeeded() {
        val background = CustomColors.WHITE
        val readable = 0xFF333333.toInt()
        assertEquals(readable, CustomColors.ensureContrast(readable, background, 4.5, CustomColors.BLACK))

        val pale = 0xFFCCCCCC.toInt()
        val fixed = CustomColors.ensureContrast(pale, background, 4.5, CustomColors.BLACK)
        assertTrue(CustomColors.contrast(fixed, background) >= 4.5)
        // No se pasa de largo hasta el negro.
        assertTrue(CustomColors.contrast(fixed, background) < 6.0)

        // Imposible: devuelve el extremo.
        assertEquals(CustomColors.BLACK, CustomColors.ensureContrast(pale, 0xFF808080.toInt(), 10.0, CustomColors.BLACK))
    }
}
