package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import app.polar.R
import app.polar.data.AppDatabase
import app.polar.data.entity.Reminder
import app.polar.data.repository.ReminderRepository
import app.polar.data.sync.SyncManager
import app.polar.data.sync.touched
import app.polar.util.AlarmManagerHelper
import app.polar.util.AlarmRescheduler
import app.polar.util.GeofenceHelper
import app.polar.util.NotificationHelper
import app.polar.util.syncReminderAlarm
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@HiltViewModel
class RemindersViewModel @Inject constructor(
    application: Application,
    private val repository: ReminderRepository,
    private val alarmHelper: AlarmManagerHelper,
    private val syncManager: SyncManager,
    private val geofenceHelper: GeofenceHelper,
    private val alarmRescheduler: AlarmRescheduler
) : AndroidViewModel(application) {

    val allReminders: StateFlow<List<Reminder>> = repository.allRemindersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    val activeReminders: StateFlow<List<Reminder>> = repository.activeRemindersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val calendarRange = MutableStateFlow<Pair<Long, Long>?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val calendarReminders: StateFlow<List<Reminder>> = calendarRange
        .flatMapLatest { range ->
            if (range == null) {
                flowOf(emptyList<Reminder>())
            } else {
                repository.getRemindersBetweenDatesFlow(range.first, range.second)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setCalendarRange(start: Long, end: Long) {
        calendarRange.value = Pair(start, end)
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "error: ${e.message}"
        }
    }

    /** Crea el recordatorio si es nuevo (id 0) o guarda los cambios del existente. */
    fun save(reminder: Reminder) = if (reminder.id == 0L) insert(reminder) else update(reminder)

    private fun insert(reminder: Reminder) = safeLaunch {
        val id = repository.insert(reminder)
        syncAlerts(reminder.copy(id = id))
        
        // Show confirmation notification immediately
        NotificationHelper.showCreationConfirmation(getApplication(), reminder.title, reminder.dateTime)
    }

    fun update(reminder: Reminder) = safeLaunch {
        repository.update(reminder.touched())
        syncAlerts(reminder)
    }

    /** Posponer desde la lista: un recordatorio no tiene más fecha que la de su aviso. */
    fun snooze(reminder: Reminder, until: Long) = update(reminder.copy(dateTime = until, isCompleted = false))

    fun moveToTrash(reminder: Reminder) = safeLaunch {
        repository.softDelete(reminder)
        syncAlerts(reminder.copy(isDeleted = true))
    }

    fun restoreFromTrash(reminder: Reminder) = safeLaunch {
        repository.restore(reminder)
        syncAlerts(reminder.copy(isDeleted = false))
    }

    /**
     * Vuelve a registrar los avisos por llegada: se pierden al reiniciar y no se pueden registrar
     * mientras falta el permiso de ubicación, que el usuario puede conceder desde los ajustes.
     */
    fun refreshArrivalAlerts() = viewModelScope.launch {
        // Sin escrituras: no hace falta la sincronización que lanza safeLaunch.
        runCatching { alarmRescheduler.rescheduleArrivalAlerts() }.onFailure { it.printStackTrace() }
    }

    private fun syncAlerts(reminder: Reminder) {
        alarmHelper.syncReminderAlarm(reminder)
        geofenceHelper.sync(reminder)
    }
    
    // Forces a sync attempt first so as many trashed reminders as possible have their tombstone
    // confirmed on the server before the local purge runs (see repository.emptyTrash /
    // ReminderDao.emptyTrash for why the purge itself refuses dirty rows).
    fun emptyTrash() = viewModelScope.launch {
        try {
            runCatching { syncManager.sync() }
            var stillInTrash = repository.emptyTrash()
            if (stillInTrash > 0 && !syncManager.isSignedIn()) {
                stillInTrash = repository.emptyTrash(force = true)
            }
            if (stillInTrash > 0) {
                _errorMessage.value = getApplication<Application>().getString(R.string.trash_purge_pending_sync_count, stillInTrash)
            }
            app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "error: ${e.message}"
        }
    }

    fun permanentDelete(reminder: Reminder) = viewModelScope.launch {
        try {
            runCatching { syncManager.sync() }
            var purged = repository.permanentDelete(reminder.id)
            // Sin cuenta vinculada no hay tombstone que confirmar en ningun servidor: el
            // guardia de sincronizacion dejaria el recordatorio atascado para siempre.
            if (!purged && !syncManager.isSignedIn()) {
                purged = repository.permanentDelete(reminder.id, force = true)
            }
            alarmHelper.cancelReminderAlarm(reminder.id)
            geofenceHelper.cancel(reminder.id)
            if (!purged) {
                _errorMessage.value = getApplication<Application>().getString(R.string.trash_item_purge_pending_sync)
            }
            app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "error: ${e.message}"
        }
    }

    fun getDeletedReminders(): LiveData<List<Reminder>> {
        return repository.getDeletedReminders()
    }
    
    fun getDeletedRemindersFlow(): StateFlow<List<Reminder>> {
        return repository.getDeletedRemindersFlow()
             .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun toggleCompletion(reminder: Reminder) = update(reminder.copy(isCompleted = !reminder.isCompleted))


}
