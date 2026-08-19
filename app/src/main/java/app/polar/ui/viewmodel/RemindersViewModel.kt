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
import app.polar.receiver.AlarmReceiver
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
    private val alarmHelper: app.polar.util.AlarmManagerHelper,
    private val syncManager: SyncManager
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
            _errorMessage.value = "Error: ${e.message}"
        }
    }

    fun insert(
        title: String, 
        description: String, 
        dateTime: Long, 
        latitude: Double? = null, 
        longitude: Double? = null, 
        radius: Float? = null, 
        locationName: String? = null
    ) = safeLaunch {
        val reminder = Reminder(
            title = title, 
            description = description, 
            dateTime = dateTime,
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            locationName = locationName
        )
        val id = repository.insert(reminder)
        alarmHelper.scheduleReminderAlarm(id, dateTime)
        
        // Show confirmation notification immediately
        app.polar.util.NotificationHelper.showCreationConfirmation(getApplication(), title, dateTime)
    }

    fun update(reminder: Reminder) = safeLaunch {
        repository.update(reminder.touched())
        if (!reminder.isCompleted) {
            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
        } else {
            alarmHelper.cancelReminderAlarm(reminder.id)
        }
    }

    fun moveToTrash(reminder: Reminder) = safeLaunch {
        repository.softDelete(reminder)
        alarmHelper.cancelReminderAlarm(reminder.id)
    }

    fun restoreFromTrash(reminder: Reminder) = safeLaunch {
        repository.restore(reminder)
        if (!reminder.isCompleted) {
            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
        }
    }
    
    // Forces a sync attempt first so as many trashed reminders as possible have their tombstone
    // confirmed on the server before the local purge runs (see repository.emptyTrash /
    // ReminderDao.emptyTrash for why the purge itself refuses dirty rows).
    fun emptyTrash() = viewModelScope.launch {
        try {
            runCatching { syncManager.sync() }
            val stillInTrash = repository.emptyTrash()
            if (stillInTrash > 0) {
                _errorMessage.value = getApplication<Application>().getString(R.string.trash_purge_pending_sync_count, stillInTrash)
            }
            app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Error: ${e.message}"
        }
    }

    fun permanentDelete(reminder: Reminder) = viewModelScope.launch {
        try {
            runCatching { syncManager.sync() }
            val purged = repository.permanentDelete(reminder.id)
            alarmHelper.cancelReminderAlarm(reminder.id)
            if (!purged) {
                _errorMessage.value = getApplication<Application>().getString(R.string.trash_item_purge_pending_sync)
            }
            app.polar.worker.SyncWorker.triggerImmediateSync(getApplication())
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Error: ${e.message}"
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

    fun toggleCompletion(reminder: Reminder) = safeLaunch {
        val updated = reminder.copy(isCompleted = !reminder.isCompleted)
        update(updated)
    }


}
