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
  version = 14,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun taskListDao(): TaskListDao
  abstract fun taskDao(): TaskDao
  abstract fun subtaskDao(): SubtaskDao
  abstract fun reminderDao(): ReminderDao
  
  companion object {
    val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE task_lists ADD COLUMN homeOrderIndex INTEGER NOT NULL DEFAULT 0")
        }
    }
    
    val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
        }
    }

    val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE reminders ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE task_lists ADD COLUMN isDependencyChain INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE tasks ADD COLUMN imageUri TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE subtasks ADD COLUMN dueDate INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN timeEstimate INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE reminders ADD COLUMN latitude REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE reminders ADD COLUMN longitude REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE reminders ADD COLUMN radius REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE reminders ADD COLUMN locationName TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE task_lists ADD COLUMN color TEXT NOT NULL DEFAULT '#7F52FF'")
        }
    }

    @Volatile
    private var INSTANCE: AppDatabase? = null
    
    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "polar_database"
        )
        .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
