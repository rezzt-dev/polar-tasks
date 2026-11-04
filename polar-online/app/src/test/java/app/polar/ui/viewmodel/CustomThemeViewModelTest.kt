package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.CustomThemePalette.Priority
import app.polar.util.FakeSharedPreferences
import app.polar.util.MainDispatcherRule
import app.polar.util.ThemeManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        every { application.getSharedPreferences(any(), any()) } returns prefs
        themeManager = ThemeManager(application)
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

        viewModel.selectAccent("cyan")
        assertTrue(viewModel.uiState.value.hasChanges)
        assertTrue(viewModel.uiState.value.canApply)

        viewModel.selectAccent("pink")
        assertFalse(viewModel.uiState.value.canApply)
    }

    @Test
    fun selections_updateTheDraftAndSurviveRecreation() {
        val savedState = SavedStateHandle()
        val viewModel = viewModel(savedState)

        viewModel.selectBackground("midnight")
        viewModel.selectAccent("amber")
        viewModel.selectPriorityColor(Priority.LOW, "teal")

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
        viewModel.selectBackground("cocoa")
        viewModel.selectAccent("red")

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
        viewModel.selectAccent("green")

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
}
