package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.polar.R
import app.polar.data.sync.SyncManager
import app.polar.data.sync.SyncPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val supabaseClient: SupabaseClient,
    private val syncManager: SyncManager,
    private val syncPrefs: SyncPrefs
) : AndroidViewModel(application) {

    val currentUserEmail: String?
        get() = supabaseClient.auth.currentUserOrNull()?.email

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    // Reflects supabaseClient.auth.sessionStatus reactively instead of a manually-written boolean
    // (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.11): a session revoked or
    // expired from outside signIn()/signOut() — e.g. a refresh failing while the user never
    // touched this screen — now updates this screen the moment it happens rather than only when
    // signIn()/signOut() are called.
    val signedIn: StateFlow<Boolean> = supabaseClient.auth.sessionStatus
        .map { it is SessionStatus.Authenticated }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), currentUserEmail != null)

    // Shown once, right when a refresh failure is observed, instead of just going quiet — see
    // hallazgo 4.11 ("ningún error en ningún log ni estado que indique que la sincronización real
    // se detuvo").
    init {
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.RefreshFailure) {
                    _errorMessage.value = getApplication<Application>().getString(R.string.auth_session_expired)
                }
            }
        }
    }

    // Set right after a successful signIn() when this device has never synced before and already
    // holds local data — the UI must ask the user to choose between merging it into the account
    // or discarding it in favor of the cloud (doc 04, "Primera sincronización";
    // agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.2) before any sync runs.
    private val _pendingMergeDecision = MutableStateFlow(false)
    val pendingMergeDecision: StateFlow<Boolean> = _pendingMergeDecision.asStateFlow()

    fun clearMessages() {
        _errorMessage.value = null
        _infoMessage.value = null
    }

    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        _isLoading.value = true
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = e.message ?: getApplication<Application>().getString(R.string.auth_error_empty_fields)
        } finally {
            _isLoading.value = false
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _errorMessage.value = getApplication<Application>().getString(R.string.auth_error_empty_fields)
            return
        }
        safeLaunch {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            if (currentUserEmail != null) {
                if (syncPrefs.lastSyncAt == 0L && syncManager.hasLocalData()) {
                    _pendingMergeDecision.value = true
                } else {
                    app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
                }
            }
            _infoMessage.value = getApplication<Application>().getString(R.string.auth_signed_in_ok)
        }
    }

    // "Subir mis datos locales" — the default push-then-pull merge, same as any other sync.
    fun confirmMergeUpload() {
        _pendingMergeDecision.value = false
        app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
    }

    // "Descartar datos locales y usar solo la nube" — destructive locally, confirmed by the UI
    // before calling this.
    fun confirmDiscardLocalUseCloud() = safeLaunch {
        _pendingMergeDecision.value = false
        val result = syncManager.discardLocalAndPullFromCloud()
        _infoMessage.value = if (result.isSuccess) {
            getApplication<Application>().getString(R.string.auth_merge_discard_success)
        } else {
            null
        }
        if (result.isFailure) {
            _errorMessage.value = result.exceptionOrNull()?.message
                ?: getApplication<Application>().getString(R.string.auth_merge_discard_error)
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = getApplication<Application>().getString(R.string.auth_reset_password_error_empty_email)
            return
        }
        safeLaunch {
            supabaseClient.auth.resetPasswordForEmail(email, redirectUrl = null)
            _infoMessage.value = getApplication<Application>().getString(R.string.auth_reset_password_sent_ok)
        }
    }

    fun signOut() {
        safeLaunch {
            supabaseClient.auth.signOut()
        }
    }
}
