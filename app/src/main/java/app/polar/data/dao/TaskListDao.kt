package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.TaskList

@Dao
interface TaskListDao {
  @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL ORDER BY orderIndex ASC")
  fun getAllLists(): LiveData<List<TaskList>>

  @Query("SELECT * FROM task_lists WHERE deletedAt IS NULL ORDER BY orderIndex ASC")
  suspend fun getAllTaskListsSnapshot(): List<TaskList>

  // Includes soft-deleted rows, unlike getAllTaskListsSnapshot() — needed by the "full overwrite"
  // backup so local tombstones also get force-pushed, not just the lists still visible in the UI.
  @Query("SELECT * FROM task_lists")
  suspend fun getAllTaskListsIncludingDeletedSnapshot(): List<TaskList>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(taskList: TaskList): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(taskLists: List<TaskList>)

  @Update
  suspend fun update(taskList: TaskList)

  @Update
  suspend fun updateAll(taskLists: List<TaskList>)

  @Query("DELETE FROM task_lists")
  suspend fun deleteAll()

  @Query("SELECT * FROM task_lists WHERE id = :id")
  suspend fun getListById(id: Long): TaskList?

  @Query("SELECT * FROM task_lists WHERE dirty = 1")
  suspend fun getDirtyTaskLists(): List<TaskList>

  @Query("SELECT * FROM task_lists WHERE uuid = :uuid LIMIT 1")
  suspend fun getByUuid(uuid: String): TaskList?
}
