package app.polar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import app.polar.data.entity.Task

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks WHERE listId = :listId AND isDeleted = 0 ORDER BY orderIndex ASC")
  fun getTasksForList(listId: Long): LiveData<List<Task>>

  @Query("SELECT * FROM tasks WHERE listId = :listId AND isDeleted = 0 ORDER BY orderIndex ASC")
  fun getTasksForListFlow(listId: Long): kotlinx.coroutines.flow.Flow<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY createdAt DESC")
  fun getAllTasks(): LiveData<List<Task>>

  @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY createdAt DESC")
  fun getAllTasksFlow(): kotlinx.coroutines.flow.Flow<List<Task>>

  @Query("SELECT * FROM tasks")
  suspend fun getAllTasksSnapshot(): List<Task>

  @Query("SELECT * FROM tasks WHERE listId = :listId")
  suspend fun getAllTasksForListSnapshot(listId: Long): List<Task>

  @Query("SELECT * FROM tasks WHERE dirty = 1")
  suspend fun getDirtyTasks(): List<Task>

  @Query("SELECT * FROM tasks WHERE uuid = :uuid LIMIT 1")
  suspend fun getByUuid(uuid: String): Task?

  // Local-only cache pointer (content:// URI for the downloaded/attached image) — never marks
  // the row dirty, since imageUri itself never travels to Supabase (see doc 04).
  @Query("UPDATE tasks SET imageUri = :imageUri WHERE id = :taskId")
  suspend fun updateImageUriCache(taskId: Long, imageUri: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(task: Task): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(tasks: List<Task>)
  
  @Update
  suspend fun update(task: Task)

  @Update
  suspend fun updateAll(tasks: List<Task>)

  @Query("DELETE FROM tasks")
  suspend fun deleteAll()

  // Physical delete only proceeds when the tombstone (and any subtask tombstone) already made it
  // to Supabase (dirty = 0) — otherwise it disappears locally without the server ever finding out,
  // and the next pull resurrects it (agent-docs/analisis-implementacion-supabase-sync.md, 3.1/3.2).
  // The NOT EXISTS guard also stops Room's ON DELETE CASCADE from silently wiping out subtasks
  // that still have unsynced changes.
  @Query("""
    DELETE FROM tasks
    WHERE id = :taskId
      AND dirty = 0
      AND NOT EXISTS (SELECT 1 FROM subtasks WHERE subtasks.taskId = tasks.id AND subtasks.dirty = 1)
  """)
  suspend fun permanentDelete(taskId: Long): Int

  @Query("""
    DELETE FROM tasks
    WHERE isDeleted = 1
      AND dirty = 0
      AND NOT EXISTS (SELECT 1 FROM subtasks WHERE subtasks.taskId = tasks.id AND subtasks.dirty = 1)
  """)
  suspend fun emptyTrash(): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 1")
  suspend fun getTrashCount(): Int

  // Trashed tasks whose tombstone already made it to Supabase — candidates to check against the
  // server for a physical purge (see SyncManager.purgeTombstonesMissingRemote,
  // agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.5).
  @Query("SELECT * FROM tasks WHERE isDeleted = 1 AND dirty = 0")
  suspend fun getConfirmedTrashedTasksSnapshot(): List<Task>

  @Query("SELECT * FROM tasks WHERE isDeleted = 1 ORDER BY createdAt DESC")
  fun getDeletedTasks(): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE id = :id")
  suspend fun getTaskById(id: Long): Task?
  
  @Query("""
    SELECT * FROM tasks 
    WHERE (title LIKE '%' || :query || '%' 
    OR description LIKE '%' || :query || '%'
    OR tags LIKE '%' || :query || '%')
    AND isDeleted = 0
    ORDER BY createdAt DESC
  """)
  fun searchTasks(query: String): LiveData<List<Task>>
  
  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end AND isDeleted = 0")
  suspend fun getTasksBetweenDates(start: Long, end: Long): List<Task>
  
  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end AND isDeleted = 0 ORDER BY dueDate ASC")
  fun getTasksForDateLive(start: Long, end: Long): LiveData<List<Task>>

  @Query("""
    SELECT 
        tasks.*, 
        task_lists.title as listTitle,
        task_lists.isDependencyChain as isDependencyChain,
        (SELECT COUNT(*) FROM subtasks WHERE taskId = tasks.id) as totalSubtasks,
        (SELECT COUNT(*) FROM subtasks WHERE taskId = tasks.id AND completed = 1) as completedSubtasks
    FROM tasks 
    LEFT JOIN task_lists ON tasks.listId = task_lists.id 
    WHERE tasks.isDeleted = 0
    ORDER BY task_lists.homeOrderIndex ASC, tasks.orderIndex ASC
  """)
  fun getTasksWithListTitles(): LiveData<List<app.polar.data.model.TaskWithList>>

  // --- Statistics queries ---
  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0")
  suspend fun getTotalTaskCount(): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND completed = 1")
  suspend fun getCompletedTaskCount(): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND completed = 0")
  suspend fun getPendingTaskCount(): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND completed = 1 AND dueDate BETWEEN :start AND :end")
  suspend fun getCompletedTaskCountBetween(start: Long, end: Long): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND dueDate BETWEEN :start AND :end")
  suspend fun getTaskCountBetween(start: Long, end: Long): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND createdAt BETWEEN :start AND :end")
  suspend fun getCreatedTaskCountBetween(start: Long, end: Long): Int

  @Query("SELECT COUNT(*) FROM tasks WHERE isDeleted = 0 AND completed = 0 AND dueDate < :now AND dueDate IS NOT NULL")
  suspend fun getOverdueTaskCount(now: Long): Int

  @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND completed = 1 ORDER BY createdAt DESC")
  suspend fun getAllCompletedTasksSnapshot(): List<Task>

  @Query("""
    SELECT * FROM tasks
    WHERE isDeleted = 0
    ORDER BY completed ASC, priority DESC, createdAt DESC, title ASC
  """)
  fun getTasksUnmarkFirst(): kotlinx.coroutines.flow.Flow<List<Task>>

  @Query("""
    SELECT priority, COUNT(*) as count
    FROM tasks
    WHERE isDeleted = 0
    GROUP BY priority
    ORDER BY priority DESC
  """)
  suspend fun getTaskCountByPriority(): List<app.polar.data.model.PriorityCount>

  @Query("""
    SELECT
      task_lists.id as listId,
      task_lists.title as title,
      COUNT(tasks.id) as total,
      COUNT(CASE WHEN tasks.completed = 1 THEN tasks.id END) as completed
    FROM task_lists
    LEFT JOIN tasks ON task_lists.id = tasks.listId AND tasks.isDeleted = 0
    GROUP BY task_lists.id
    ORDER BY total DESC
  """)
  suspend fun getTaskCountByList(): List<app.polar.data.model.ListTaskCount>
}
