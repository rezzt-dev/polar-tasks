package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import app.polar.data.AppDatabase
import app.polar.data.entity.Reminder
import app.polar.data.repository.ReminderRepository
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
        repository.update(reminder)
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
    fun refreshArrivalAlerts() = safeLaunch {
        alarmRescheduler.rescheduleArrivalAlerts()
    }

    private fun syncAlerts(reminder: Reminder) {
        alarmHelper.syncReminderAlarm(reminder)
        geofenceHelper.sync(reminder)
    }

    fun emptyTrash() = safeLaunch {
        repository.emptyTrash()
    }

    fun permanentDelete(reminder: Reminder) = safeLaunch {
        repository.permanentDelete(reminder.id)
        alarmHelper.cancelReminderAlarm(reminder.id)
        geofenceHelper.cancel(reminder.id)
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
