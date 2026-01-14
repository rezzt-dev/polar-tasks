package app.polar.data.repository

import androidx.lifecycle.LiveData
import app.polar.data.dao.ReminderDao
import app.polar.data.entity.Reminder

import javax.inject.Inject

class ReminderRepository @Inject constructor(private val reminderDao: ReminderDao) {
    
    val allReminders: LiveData<List<Reminder>> = reminderDao.getAllReminders()
    val activeReminders: LiveData<List<Reminder>> = reminderDao.getActiveReminders()

    val allRemindersFlow: kotlinx.coroutines.flow.Flow<List<Reminder>> = reminderDao.getAllRemindersFlow()
    val activeRemindersFlow: kotlinx.coroutines.flow.Flow<List<Reminder>> = reminderDao.getActiveRemindersFlow()

    suspend fun insert(reminder: Reminder): Long {
        return reminderDao.insert(reminder)
    }

    suspend fun update(reminder: Reminder) {
        reminderDao.update(reminder)
    }

    suspend fun delete(reminder: Reminder) {
        reminderDao.delete(reminder)
    }

    suspend fun getReminderById(id: Long): Reminder? {
        return reminderDao.getReminderById(id)
    }

    // Trash operations
    suspend fun softDelete(id: Long) {
        reminderDao.softDelete(id)
    }

    suspend fun restore(id: Long) {
        reminderDao.restore(id)
    }

    suspend fun permanentDelete(id: Long) {
        reminderDao.permanentDelete(id)
    }

    suspend fun emptyTrash() {
        reminderDao.emptyTrash()
    }

    fun getDeletedReminders(): LiveData<List<Reminder>> {
        return reminderDao.getDeletedReminders()
    }

    fun getDeletedRemindersFlow(): kotlinx.coroutines.flow.Flow<List<Reminder>> {
        return reminderDao.getDeletedRemindersFlow()
    }
}
