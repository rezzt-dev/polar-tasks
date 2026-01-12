package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Subtask

@Dao
interface SubtaskDao {
  @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY id ASC")
  fun getSubtasksForTask(taskId: Long): LiveData<List<Subtask>>
  
  @Query("SELECT * FROM subtasks WHERE taskId = :taskId")
  suspend fun getSubtasksForTaskDirect(taskId: Long): List<Subtask>
  
  @Insert
  suspend fun insert(subtask: Subtask): Long
  
  @Update
  suspend fun update(subtask: Subtask)
  
  @Delete
  suspend fun delete(subtask: Subtask)
  
  @Query("DELETE FROM subtasks WHERE taskId = :taskId")
  suspend fun deleteAllForTask(taskId: Long)
}
