package app.polar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.polar.util.AlertActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aviso que se pospone desde su notificación: el de una tarea o el de un recordatorio. */
data class SnoozeTarget(val kind: Kind, val id: Long) {
    enum class Kind { TASK, REMINDER }
}

@HiltViewModel
class SnoozeViewModel @Inject constructor(
    private val alertActions: AlertActions
) : ViewModel() {

    sealed interface Outcome {
        data class Snoozed(val until: Long) : Outcome
        /** Se completó o se borró mientras la notificación seguía visible. */
        data object NotPending : Outcome
    }

    private val _outcome = MutableStateFlow<Outcome?>(null)
    val outcome: StateFlow<Outcome?> = _outcome.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var snoozing = false

    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "error: ${e.message}"
        }
    }

    fun snooze(target: SnoozeTarget, until: Long) {
        // Una sola escritura aunque llegue más de una elección (doble toque).
        if (snoozing) return
        snoozing = true
        safeLaunch {
            val applied = when (target.kind) {
                SnoozeTarget.Kind.TASK -> alertActions.snoozeTask(target.id, until)
                SnoozeTarget.Kind.REMINDER -> alertActions.snoozeReminder(target.id, until)
            }
            _outcome.value = if (applied) Outcome.Snoozed(until) else Outcome.NotPending
        }
    }
}
