package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import app.polar.data.AppDatabase
import app.polar.data.entity.Reminder
import app.polar.data.repository.ReminderRepository
import app.polar.receiver.AlarmReceiver
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RemindersViewModel @Inject constructor(
    application: Application,
    private val repository: ReminderRepository,
    private val alarmHelper: app.polar.util.AlarmManagerHelper
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Error: ${e.message}"
        }
    }

    fun insert(title: String, description: String, dateTime: Long) = safeLaunch {
        val reminder = Reminder(title = title, description = description, dateTime = dateTime)
        val id = repository.insert(reminder)
        alarmHelper.scheduleReminderAlarm(id, dateTime)
        
        // Show confirmation notification immediately
        app.polar.util.NotificationHelper.showCreationConfirmation(getApplication(), title, dateTime)
    }

    fun update(reminder: Reminder) = safeLaunch {
        repository.update(reminder)
        if (!reminder.isCompleted) {
            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
        } else {
            alarmHelper.cancelReminderAlarm(reminder.id)
        }
    }

    fun moveToTrash(reminder: Reminder) = safeLaunch {
        repository.softDelete(reminder.id)
        alarmHelper.cancelReminderAlarm(reminder.id)
    }

    fun restoreFromTrash(reminder: Reminder) = safeLaunch {
        repository.restore(reminder.id)
        if (!reminder.isCompleted) {
            alarmHelper.scheduleReminderAlarm(reminder.id, reminder.dateTime)
        }
    }
    
    fun emptyTrash() = safeLaunch {
        repository.emptyTrash()
    }
    
    fun permanentDelete(reminder: Reminder) = safeLaunch {
        repository.permanentDelete(reminder.id)
        alarmHelper.cancelReminderAlarm(reminder.id)
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
