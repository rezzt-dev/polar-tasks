package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.CustomColorResources
import app.polar.util.CustomThemePalette.Slot
import app.polar.util.FakeSharedPreferences
import app.polar.util.MainDispatcherRule
import app.polar.util.ThemeManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CustomThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs = FakeSharedPreferences()
    private val application = mockk<Application>(relaxed = true)
    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        CustomColorResources.supportedOverride = true
        every { application.getSharedPreferences(any(), any()) } returns prefs
        themeManager = ThemeManager(application)
    }

    @After
    fun tearDown() {
        CustomColorResources.supportedOverride = null
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        CustomThemeViewModel(application, savedState)

    @Test
    fun withoutSavedTheme_startsFromTheDefaultOfTheCurrentMode() {
        themeManager.saveTheme(ThemeManager.THEME_SAND, applyNightMode = false)
        val state = viewModel().uiState.value

        assertEquals(CustomThemePalette.DEFAULT_LIGHT, state.draft)
        assertFalse(state.isCreated)
        assertFalse(state.isActive)
        assertFalse(state.hasChanges)
        // Crear el tema con la combinacion por defecto tambien es valido.
        assertTrue(state.canApply)

        themeManager.saveTheme(ThemeManager.THEME_EMBER, applyNightMode = false)
        assertEquals(CustomThemePalette.DEFAULT_DARK, viewModel().uiState.value.draft)
    }

    @Test
    fun activeTheme_canOnlyBeSavedWhenSomethingChanged() {
        val saved = CustomTheme("plum", "pink", "red", "amber", "blue")
        themeManager.saveCustomTheme(saved)
        themeManager.saveTheme(ThemeManager.THEME_CUSTOM, applyNightMode = false)
        val viewModel = viewModel()

        assertEquals(saved, viewModel.uiState.value.draft)
        assertTrue(viewModel.uiState.value.isActive)
        assertFalse(viewModel.uiState.value.canApply)

        viewModel.select(Slot.ACCENT, "cyan")
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.canApply)

        viewModel.select(Slot.ACCENT, "pink")
        assertFalse(viewModel.uiState.value.canApply)
    }

    @Test
    fun selections_updateTheDraftAndSurviveRecreation() {
        val savedState = SavedStateHandle()
        val viewModel = viewModel(savedState)

        viewModel.select(Slot.BACKGROUND, "midnight")
        viewModel.select(Slot.ACCENT, "amber")
        viewModel.select(Slot.PRIORITY_LOW, "teal")

        val expected = CustomThemePalette.DEFAULT_DARK.copy(background = "midnight", accent = "amber", priorityLow = "teal")
        assertEquals(expected, viewModel.uiState.value.draft)
        // Nada se guarda hasta aplicar.
        assertEquals(null, themeManager.loadCustomTheme())
        // El borrador se recupera tras un giro o la muerte del proceso.
        assertEquals(expected, viewModel(savedState).uiState.value.draft)
    }

    @Test
    fun reset_goesBackToTheDefaultOfTheModeAndCanBeUndone() {
        val viewModel = viewModel()
        viewModel.select(Slot.BACKGROUND, "cocoa")
        viewModel.select(Slot.ACCENT, "red")

        val previous = viewModel.reset()
        assertEquals(CustomThemePalette.DEFAULT_DARK, viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.canReset)

        viewModel.restore(previous)
        assertEquals(CustomThemePalette.DEFAULT_DARK.copy(background = "cocoa", accent = "red"), viewModel.uiState.value.draft)
        assertTrue(viewModel.uiState.value.canReset)
    }

    @Test
    fun apply_savesTheThemeAndActivatesIt() {
        themeManager.saveTheme(ThemeManager.THEME_SAGE, applyNightMode = false)
        val viewModel = viewModel()
        viewModel.select(Slot.ACCENT, "green")

        viewModel.apply()

        assertTrue(viewModel.applied.value)
        assertEquals(ThemeManager.THEME_CUSTOM, themeManager.loadTheme())
        assertEquals(CustomThemePalette.DEFAULT_LIGHT.copy(accent = "green"), themeManager.loadCustomTheme())
    }

    @Test
    fun apply_doesNothingWhenThereIsNothingToSave() {
        val saved = CustomTheme("snow", "ink", "red", "amber", "blue")
        themeManager.saveCustomTheme(saved)
        themeManager.saveTheme(ThemeManager.THEME_CUSTOM, applyNightMode = false)
        val viewModel = viewModel()

        viewModel.apply()

        assertFalse(viewModel.applied.value)
    }

    @Test
    fun customColors_areNormalizedSavedAndAvailableWhenSupported() {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.customColorsAvailable)

        viewModel.select(Slot.ACCENT, "#3a7bd5")
        viewModel.select(Slot.BACKGROUND, "#1e1e2e")
        viewModel.select(Slot.PRIORITY_HIGH, "#F38BA8")

        val draft = viewModel.uiState.value.draft
        assertEquals("#3A7BD5", draft.accent)
        assertEquals("#1E1E2E", draft.background)
        assertEquals("#F38BA8", draft.priorityHigh)
        assertTrue(draft.isDark)

        viewModel.apply()
        assertEquals(draft, themeManager.loadCustomTheme())
    }

    @Test
    fun invalidCustomColors_fallBackToThePalette() {
        val viewModel = viewModel()
        viewModel.select(Slot.ACCENT, "#12345")
        assertEquals(CustomThemePalette.DEFAULT_DARK.accent, viewModel.uiState.value.draft.accent)
    }

    @Test
    fun customColors_areUnavailableOnOldDevices() {
        CustomColorResources.supportedOverride = false
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.customColorsAvailable)

        viewModel.select(Slot.ACCENT, "#3A7BD5")
        assertEquals(CustomThemePalette.DEFAULT_DARK.accent, viewModel.uiState.value.draft.accent)
    }

    @Test
    fun recentColors_keepTheLatestFirstWithoutDuplicates() {
        val viewModel = viewModel()
        assertTrue(viewModel.recentColors.value.isEmpty())

        viewModel.rememberRecentColor("#112233")
        viewModel.rememberRecentColor("#aabbcc")
        viewModel.rememberRecentColor("#112233")
        assertEquals(listOf("#112233", "#AABBCC"), viewModel.recentColors.value)

        repeat(ThemeManager.MAX_RECENT_CUSTOM_COLORS + 2) { i -> viewModel.rememberRecentColor("#0000%02X".format(i)) }
        assertEquals(ThemeManager.MAX_RECENT_CUSTOM_COLORS, viewModel.recentColors.value.size)
        assertEquals("#000009", viewModel.recentColors.value.first())
        // Se recuerdan entre sesiones.
        assertEquals(viewModel.recentColors.value, viewModel().recentColors.value)
    }
}
