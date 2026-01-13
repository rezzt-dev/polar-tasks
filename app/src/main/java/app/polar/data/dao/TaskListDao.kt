package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.TaskList

@Dao
interface TaskListDao {
  @Query("SELECT * FROM task_lists ORDER BY orderIndex ASC")
  fun getAllLists(): LiveData<List<TaskList>>
  
  @Query("SELECT * FROM task_lists ORDER BY orderIndex ASC")
  suspend fun getAllTaskListsSnapshot(): List<TaskList>
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(taskList: TaskList): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(taskLists: List<TaskList>)
  
  @Update
  suspend fun update(taskList: TaskList)

  @Update
  suspend fun updateAll(taskLists: List<TaskList>)
  
  @Delete
  suspend fun delete(taskList: TaskList)

  @Query("DELETE FROM task_lists")
  suspend fun deleteAll()
  
  @Query("SELECT * FROM task_lists WHERE id = :id")
  suspend fun getListById(id: Long): TaskList?
}
