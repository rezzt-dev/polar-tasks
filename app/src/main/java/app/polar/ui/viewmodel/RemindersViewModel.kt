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

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReminderRepository
    val allReminders: LiveData<List<Reminder>>
    val activeReminders: LiveData<List<Reminder>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ReminderRepository(database.reminderDao())
        allReminders = repository.allReminders
        activeReminders = repository.activeReminders
    }

    fun insert(title: String, description: String, dateTime: Long) = viewModelScope.launch {
        val reminder = Reminder(title = title, description = description, dateTime = dateTime)
        val id = repository.insert(reminder)
        scheduleAlarm(id, dateTime)
        
        // Show confirmation notification immediately
        app.polar.util.NotificationHelper.showCreationConfirmation(getApplication(), title, dateTime)
    }

    fun update(reminder: Reminder) = viewModelScope.launch {
        repository.update(reminder)
        if (!reminder.isCompleted) {
            scheduleAlarm(reminder.id, reminder.dateTime)
        } else {
            cancelAlarm(reminder.id)
        }
    }

    fun moveToTrash(reminder: Reminder) = viewModelScope.launch {
        repository.softDelete(reminder.id)
        cancelAlarm(reminder.id)
    }

    fun restoreFromTrash(reminder: Reminder) = viewModelScope.launch {
        repository.restore(reminder.id)
        if (!reminder.isCompleted) {
            scheduleAlarm(reminder.id, reminder.dateTime)
        }
    }
    
    fun emptyTrash() = viewModelScope.launch {
        repository.emptyTrash()
    }
    
    fun permanentDelete(reminder: Reminder) = viewModelScope.launch {
        repository.permanentDelete(reminder.id)
        cancelAlarm(reminder.id)
    }

    fun getDeletedReminders(): LiveData<List<Reminder>> {
        return repository.getDeletedReminders()
    }

    fun toggleCompletion(reminder: Reminder) = viewModelScope.launch {
        val updated = reminder.copy(isCompleted = !reminder.isCompleted)
        update(updated)
    }

    private fun scheduleAlarm(reminderId: Long, timeInMillis: Long) {
        if (timeInMillis <= System.currentTimeMillis()) return

        val alarmManager = getApplication<Application>().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(getApplication(), AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmReceiver.EXTRA_TYPE, AlarmReceiver.TYPE_REMINDER)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            getApplication(),
            1000000 + reminderId.toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun cancelAlarm(reminderId: Long) {
        val alarmManager = getApplication<Application>().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(getApplication(), AlarmReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            getApplication(),
            1000000 + reminderId.toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
