package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.polar.util.CustomTheme
import app.polar.util.CustomThemePalette
import app.polar.util.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado del editor del tema personalizado. La combinacion en edicion ([UiState.draft]) vive en
 * [SavedStateHandle] para sobrevivir a giros y a la muerte del proceso; solo se guarda en
 * preferencias al aplicarla.
 */
@HiltViewModel
class CustomThemeViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    data class UiState(
        /** Combinacion que se esta editando y que pinta la vista previa. */
        val draft: CustomTheme,
        /** Combinacion de partida: la guardada o, si aun no existe, la de por defecto. */
        val baseline: CustomTheme,
        /** El tema personalizado ya se habia creado antes de abrir el editor. */
        val isCreated: Boolean,
        /** El tema personalizado es el tema activo. */
        val isActive: Boolean
    ) {
        val hasChanges: Boolean get() = draft != baseline

        /** Aplicar tiene sentido si activa el tema o si guarda algun cambio. */
        val canApply: Boolean get() = !isActive || hasChanges

        val canReset: Boolean get() = draft != CustomThemePalette.defaultFor(draft.isDark)
    }

    private val themeManager = ThemeManager(application)

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Se pone a `true` una vez guardado y activado el tema: la pantalla se cierra. */
    private val _applied = MutableStateFlow(false)
    val applied: StateFlow<Boolean> = _applied.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var applying = false

    private fun initialState(): UiState {
        val saved = themeManager.loadCustomTheme()
        // Sin tema creado se parte del modo (claro u oscuro) que el usuario tiene ahora.
        val baseline = saved ?: CustomThemePalette.defaultFor(dark = !themeManager.isLightTheme())
        return UiState(
            draft = CustomTheme.parse(savedStateHandle[KEY_DRAFT]) ?: baseline,
            baseline = baseline,
            isCreated = saved != null,
            isActive = saved != null && themeManager.loadTheme() == ThemeManager.THEME_CUSTOM
        )
    }

    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "error: ${e.message}"
        }
    }

    fun selectBackground(key: String) = updateDraft { it.copy(background = key) }

    fun selectAccent(key: String) = updateDraft { it.copy(accent = key) }

    fun selectPriorityColor(priority: CustomThemePalette.Priority, key: String) =
        updateDraft { it.withPriorityColor(priority, key) }

    /** Vuelve a la combinacion por defecto del modo actual; devuelve la anterior para deshacer. */
    fun reset(): CustomTheme {
        val previous = _uiState.value.draft
        updateDraft { CustomThemePalette.defaultFor(it.isDark) }
        return previous
    }

    /** Recupera una combinacion anterior (deshacer el restablecimiento). */
    fun restore(theme: CustomTheme) = updateDraft { CustomThemePalette.sanitize(theme) }

    /** Guarda la combinacion y la deja como tema activo; el modo noche se aplica al volver a ajustes. */
    fun apply() {
        if (applying || !_uiState.value.canApply) return
        applying = true
        safeLaunch {
            try {
                themeManager.saveCustomTheme(_uiState.value.draft)
                themeManager.saveTheme(ThemeManager.THEME_CUSTOM, applyNightMode = false)
                _applied.value = true
            } finally {
                applying = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun updateDraft(transform: (CustomTheme) -> CustomTheme) {
        _uiState.update { state ->
            val draft = CustomThemePalette.sanitize(transform(state.draft))
            savedStateHandle[KEY_DRAFT] = draft.serialize()
            state.copy(draft = draft)
        }
    }

    private companion object {
        const val KEY_DRAFT = "custom_theme_draft"
    }
}
