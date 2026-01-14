package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Reminder

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY dateTime ASC")
    fun getAllReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY dateTime ASC")
    fun getAllRemindersFlow(): kotlinx.coroutines.flow.Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND isDeleted = 0 ORDER BY dateTime ASC")
    fun getActiveReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND isDeleted = 0 ORDER BY dateTime ASC")
    fun getActiveRemindersFlow(): kotlinx.coroutines.flow.Flow<List<Reminder>>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("UPDATE reminders SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE reminders SET isDeleted = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM reminders WHERE isDeleted = 1")
    suspend fun emptyTrash()
    
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun permanentDelete(id: Long)

    @Query("SELECT * FROM reminders WHERE isDeleted = 1 ORDER BY dateTime DESC")
    fun getDeletedReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isDeleted = 1 ORDER BY dateTime DESC")
    fun getDeletedRemindersFlow(): kotlinx.coroutines.flow.Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): Reminder?
    
    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersSnapshot(): List<Reminder>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)
}
