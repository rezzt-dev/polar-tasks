package app.polar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
  version = 18,
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

    // Migraciones 14 -> 17 (v1.6): añadieron columnas de control para la sincronización en la
    // nube (uuid, updatedAt, deletedAt, dirty e imagePath), además de subtasks.orderIndex y
    // subtasks.createdAt. Se conservan tal cual para que cualquier instalación antigua pueda
    // actualizar; MIGRATION_17_18 retira después todo lo que era exclusivo de la sincronización.
    //
    // uuid se añade con un valor por defecto temporal '' (SQLite exige un valor constante para
    // añadir una columna NOT NULL a una tabla con filas) y se rellena fila a fila antes de crear
    // el índice único, para que ninguna fila quede con un uuid vacío duplicado.
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

    // subtasks no tenía createdAt; las filas existentes lo toman de updatedAt como aproximación.
    val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE subtasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE subtasks SET createdAt = updatedAt")
        }
    }

    val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
        override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN imagePath TEXT DEFAULT NULL")
        }
    }

    // Polar vuelve a ser una app 100 % local: se retiran las columnas de sincronización
    // (uuid, updatedAt, deletedAt, dirty e imagePath) recreando cada tabla, y antes se aplican de
    // forma definitiva los borrados lógicos que solo existían para poder propagarse a la nube.
    // Aprovecha la recreación para indexar las claves foráneas tasks.listId y subtasks.taskId.
    val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Al mandar una tarea a la papelera, la v1.6 marcaba también sus subtareas como
            // borradas. En el modelo local la papelera no toca las subtareas (restaurar la tarea
            // las recupera), así que se reactivan las que se borraron junto a su tarea.
            database.execSQL(
                """
                UPDATE subtasks SET deletedAt = NULL
                WHERE deletedAt IS NOT NULL AND EXISTS (
                    SELECT 1 FROM tasks
                    WHERE tasks.id = subtasks.taskId
                      AND tasks.isDeleted = 1
                      AND tasks.deletedAt IS NOT NULL
                      AND subtasks.deletedAt >= tasks.deletedAt
                )
                """.trimIndent()
            )

            // Listas eliminadas: se borran junto a sus tareas y subtareas, como hace ahora
            // TaskRepository.deleteTaskList(). Las claves foráneas no se aplican durante una
            // migración, así que la cascada se hace a mano y se limpian también huérfanos.
            database.execSQL(
                "DELETE FROM subtasks WHERE taskId IN " +
                    "(SELECT id FROM tasks WHERE listId IN (SELECT id FROM task_lists WHERE deletedAt IS NOT NULL))"
            )
            database.execSQL("DELETE FROM tasks WHERE listId IN (SELECT id FROM task_lists WHERE deletedAt IS NOT NULL)")
            database.execSQL("DELETE FROM task_lists WHERE deletedAt IS NOT NULL")
            database.execSQL("DELETE FROM subtasks WHERE deletedAt IS NOT NULL")
            database.execSQL("DELETE FROM tasks WHERE listId NOT IN (SELECT id FROM task_lists)")
            database.execSQL("DELETE FROM subtasks WHERE taskId NOT IN (SELECT id FROM tasks)")

            recreateTable(
                database,
                table = "task_lists",
                createSql = "CREATE TABLE IF NOT EXISTS `{table}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `icon` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`orderIndex` INTEGER NOT NULL, `homeOrderIndex` INTEGER NOT NULL, " +
                    "`isDependencyChain` INTEGER NOT NULL, `color` TEXT NOT NULL)",
                columns = "`id`, `title`, `icon`, `createdAt`, `orderIndex`, `homeOrderIndex`, `isDependencyChain`, `color`"
            )
            recreateTable(
                database,
                table = "tasks",
                createSql = "CREATE TABLE IF NOT EXISTS `{table}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`listId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                    "`completed` INTEGER NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`dueDate` INTEGER, `orderIndex` INTEGER NOT NULL, `recurrence` TEXT NOT NULL, " +
                    "`isDeleted` INTEGER NOT NULL, `priority` INTEGER NOT NULL, `imageUri` TEXT, " +
                    "`timeEstimate` INTEGER NOT NULL, FOREIGN KEY(`listId`) REFERENCES `task_lists`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
                columns = "`id`, `listId`, `title`, `description`, `completed`, `tags`, `createdAt`, `dueDate`, " +
                    "`orderIndex`, `recurrence`, `isDeleted`, `priority`, `imageUri`, `timeEstimate`",
                indexSql = "CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `tasks` (`listId`)"
            )
            recreateTable(
                database,
                table = "subtasks",
                createSql = "CREATE TABLE IF NOT EXISTS `{table}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `completed` INTEGER NOT NULL, " +
                    "`dueDate` INTEGER, `orderIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                columns = "`id`, `taskId`, `title`, `completed`, `dueDate`, `orderIndex`, `createdAt`",
                indexSql = "CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)"
            )
            recreateTable(
                database,
                table = "reminders",
                createSql = "CREATE TABLE IF NOT EXISTS `{table}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `description` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, " +
                    "`isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    "`latitude` REAL, `longitude` REAL, `radius` REAL, `locationName` TEXT)",
                columns = "`id`, `title`, `description`, `dateTime`, `isCompleted`, `createdAt`, `isDeleted`, " +
                    "`latitude`, `longitude`, `radius`, `locationName`"
            )
        }
    }

    // Copia `columns` a una tabla nueva creada con `createSql` (`{table}` se sustituye por el
    // nombre temporal), elimina la original y renombra la nueva. Mantiene el contador de
    // AUTOINCREMENT para que no se reutilicen ids de filas ya borradas: las alarmas usan el id
    // como request code, y reutilizarlo podría hacer que una alarma antigua apunte a otra tarea.
    private fun recreateTable(
        database: SupportSQLiteDatabase,
        table: String,
        createSql: String,
        columns: String,
        indexSql: String? = null
    ) {
        val tempTable = "${table}_new"
        val sequence = database.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf<Any>(table)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }

        database.execSQL(createSql.replace("{table}", tempTable))
        database.execSQL("INSERT INTO `$tempTable` ($columns) SELECT $columns FROM `$table`")
        database.execSQL("DROP TABLE `$table`")
        database.execSQL("ALTER TABLE `$tempTable` RENAME TO `$table`")
        indexSql?.let { database.execSQL(it) }

        if (sequence != null) {
            database.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf<Any>(table))
            database.execSQL(
                "INSERT INTO sqlite_sequence (name, seq) SELECT ?, MAX(?, IFNULL(MAX(id), 0)) FROM `$table`",
                arrayOf<Any>(table, sequence)
            )
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
        .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
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
