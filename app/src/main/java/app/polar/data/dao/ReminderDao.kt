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

    @Query("SELECT * FROM reminders WHERE dateTime >= :start AND dateTime <= :end AND isDeleted = 0")
    fun getRemindersBetweenDatesFlow(start: Long, end: Long): kotlinx.coroutines.flow.Flow<List<Reminder>>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Update
    suspend fun updateAll(reminders: List<Reminder>)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    // Physical delete only proceeds once the tombstone already made it to Supabase (dirty = 0) —
    // otherwise it disappears locally without the server ever finding out, and the next pull
    // resurrects it (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 3.1).
    @Query("DELETE FROM reminders WHERE isDeleted = 1 AND dirty = 0")
    suspend fun emptyTrash(): Int

    @Query("DELETE FROM reminders WHERE id = :id AND dirty = 0")
    suspend fun permanentDelete(id: Long): Int

    @Query("SELECT COUNT(*) FROM reminders WHERE isDeleted = 1")
    suspend fun getTrashCount(): Int

    // Trashed reminders whose tombstone already made it to Supabase — candidates to check against
    // the server for a physical purge (see SyncManager.purgeTombstonesMissingRemote,
    // agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.5).
    @Query("SELECT * FROM reminders WHERE isDeleted = 1 AND dirty = 0")
    suspend fun getConfirmedTrashedRemindersSnapshot(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE isDeleted = 1 ORDER BY dateTime DESC")
    fun getDeletedReminders(): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isDeleted = 1 ORDER BY dateTime DESC")
    fun getDeletedRemindersFlow(): kotlinx.coroutines.flow.Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE dirty = 1")
    suspend fun getDirtyReminders(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Reminder?
    
    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersSnapshot(): List<Reminder>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)
}
