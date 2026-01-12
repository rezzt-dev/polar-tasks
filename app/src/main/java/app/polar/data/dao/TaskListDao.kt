package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.TaskList

@Dao
interface TaskListDao {
  @Query("SELECT * FROM task_lists ORDER BY createdAt DESC")
  fun getAllLists(): LiveData<List<TaskList>>
  
  @Insert
  suspend fun insert(taskList: TaskList): Long
  
  @Update
  suspend fun update(taskList: TaskList)
  
  @Delete
  suspend fun delete(taskList: TaskList)
  
  @Query("SELECT * FROM task_lists WHERE id = :id")
  suspend fun getListById(id: Long): TaskList?
}
