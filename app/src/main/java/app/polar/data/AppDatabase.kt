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
  version = 17,
  exportSchema = true
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

    // Adds cloud-sync bookkeeping columns (uuid/updatedAt/deletedAt/dirty) to the 4 sync-eligible
    // tables plus subtasks.orderIndex, per agent-docs/supabase-sync/06-plan-implementacion-android.md.
    // uuid is added with a temporary '' default (SQLite requires a constant default to add a NOT NULL
    // column to a non-empty table) and immediately backfilled per row before the unique index is created,
    // so no row is ever left with a colliding blank uuid.
    val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE task_lists ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE task_lists ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE task_lists ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE task_lists ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

            database.execSQL("ALTER TABLE tasks ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE tasks ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

            database.execSQL("ALTER TABLE subtasks ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE subtasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE subtasks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE subtasks ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE subtasks ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE reminders ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE reminders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE reminders ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE reminders ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")

            backfillUuidAndUpdatedAt(database, "task_lists", hasCreatedAt = true)
            backfillUuidAndUpdatedAt(database, "tasks", hasCreatedAt = true)
            backfillUuidAndUpdatedAt(database, "subtasks", hasCreatedAt = false)
            backfillUuidAndUpdatedAt(database, "reminders", hasCreatedAt = true)
            backfillSubtaskOrderIndex(database)

            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_task_lists_uuid ON task_lists(uuid)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_uuid ON tasks(uuid)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_subtasks_uuid ON subtasks(uuid)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_uuid ON reminders(uuid)")
        }
    }

    private fun backfillUuidAndUpdatedAt(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        hasCreatedAt: Boolean
    ) {
        val cursor = if (hasCreatedAt) {
            database.query("SELECT id, createdAt FROM $table")
        } else {
            database.query("SELECT id FROM $table")
        }
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val createdAtIndex = if (hasCreatedAt) it.getColumnIndexOrThrow("createdAt") else -1
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val updatedAt = if (hasCreatedAt) it.getLong(createdAtIndex) else System.currentTimeMillis()
                val uuid = java.util.UUID.randomUUID().toString()
                database.execSQL(
                    "UPDATE $table SET uuid = ?, updatedAt = ? WHERE id = ?",
                    arrayOf<Any>(uuid, updatedAt, id)
                )
            }
        }
    }

    private fun backfillSubtaskOrderIndex(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        val cursor = database.query("SELECT id, taskId FROM subtasks ORDER BY taskId ASC, id ASC")
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val taskIdIndex = it.getColumnIndexOrThrow("taskId")
            var currentTaskId = -1L
            var order = 0
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val taskId = it.getLong(taskIdIndex)
                if (taskId != currentTaskId) {
                    currentTaskId = taskId
                    order = 0
                }
                database.execSQL("UPDATE subtasks SET orderIndex = ? WHERE id = ?", arrayOf<Any>(order, id))
                order++
            }
        }
    }

    // subtasks never had a createdAt column locally, but the Supabase contract (doc 05) requires
    // a non-null created_at on the wire. Backfilling it from updatedAt is an approximation for
    // pre-existing rows, but it's stored from here on so it stays stable across future pushes
    // instead of drifting forward on every edit.
    val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE subtasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE subtasks SET createdAt = updatedAt")
        }
    }

    // Storage path for the task's image (doc 03/05: `{user_id}/{task_uuid}.jpg` in the
    // task-images bucket) — distinct from imageUri, which stays the local content:// cache.
    val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN imagePath TEXT DEFAULT NULL")
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
        .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
        .fallbackToDestructiveMigration()
        // WAL allows concurrent reads + writes without blocking the UI thread
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
