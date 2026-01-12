package app.polar.data.repository

import androidx.lifecycle.LiveData
import app.polar.data.dao.ReminderDao
import app.polar.data.entity.Reminder

class ReminderRepository(private val reminderDao: ReminderDao) {
    
    val allReminders: LiveData<List<Reminder>> = reminderDao.getAllReminders()
    val activeReminders: LiveData<List<Reminder>> = reminderDao.getActiveReminders()

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
}
