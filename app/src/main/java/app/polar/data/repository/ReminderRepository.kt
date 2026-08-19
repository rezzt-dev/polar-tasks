package app.polar.data.repository

import androidx.lifecycle.LiveData
import app.polar.data.dao.ReminderDao
import app.polar.data.entity.Reminder
import app.polar.data.sync.touchedDeleted
import app.polar.data.sync.touchedRestored

import javax.inject.Inject

class ReminderRepository @Inject constructor(private val reminderDao: ReminderDao) {
    
    val allReminders: LiveData<List<Reminder>> = reminderDao.getAllReminders()
    val activeReminders: LiveData<List<Reminder>> = reminderDao.getActiveReminders()

    val allRemindersFlow: kotlinx.coroutines.flow.Flow<List<Reminder>> = reminderDao.getAllRemindersFlow()
    val activeRemindersFlow: kotlinx.coroutines.flow.Flow<List<Reminder>> = reminderDao.getActiveRemindersFlow()

    fun getRemindersBetweenDatesFlow(start: Long, end: Long): kotlinx.coroutines.flow.Flow<List<Reminder>> {
        return reminderDao.getRemindersBetweenDatesFlow(start, end)
    }

    suspend fun insert(reminder: Reminder): Long {
        return reminderDao.insert(reminder)
    }

    suspend fun update(reminder: Reminder) {
        reminderDao.update(reminder)
    }

    suspend fun getReminderById(id: Long): Reminder? {
        return reminderDao.getReminderById(id)
    }

    // Trash operations
    suspend fun softDelete(reminder: Reminder) {
        reminderDao.update(reminder.touchedDeleted())
    }

    suspend fun restore(reminder: Reminder) {
        reminderDao.update(reminder.touchedRestored())
    }

    // Returns whether the row was actually purged; false if its tombstone hasn't reached
    // Supabase yet (see ReminderDao.permanentDelete and
    // agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 3.1).
    suspend fun permanentDelete(id: Long): Boolean {
        return reminderDao.permanentDelete(id) > 0
    }

    // Returns how many trashed reminders are still stuck after the purge attempt (unsynced
    // tombstone) so the caller can warn the user instead of silently leaving them in the trash.
    suspend fun emptyTrash(): Int {
        reminderDao.emptyTrash()
        return reminderDao.getTrashCount()
    }

    fun getDeletedReminders(): LiveData<List<Reminder>> {
        return reminderDao.getDeletedReminders()
    }

    fun getDeletedRemindersFlow(): kotlinx.coroutines.flow.Flow<List<Reminder>> {
        return reminderDao.getDeletedRemindersFlow()
    }
}
