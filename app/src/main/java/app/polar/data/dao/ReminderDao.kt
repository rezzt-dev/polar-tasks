package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Reminder

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dateTime ASC")
    fun getAllReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY dateTime ASC")
    fun getActiveReminders(): LiveData<List<Reminder>>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): Reminder?
}
