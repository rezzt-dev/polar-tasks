package app.polar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import app.polar.data.dao.ReminderDao
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.entity.Reminder

@Database(
  entities = [TaskList::class, Task::class, Subtask::class, Reminder::class],
  version = 6,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun taskListDao(): TaskListDao
  abstract fun taskDao(): TaskDao
  abstract fun subtaskDao(): SubtaskDao
  abstract fun reminderDao(): ReminderDao
  
  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null
    
    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "polar_database"
        )
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
