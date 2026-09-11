package app.polar.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented tests for the Room migrations that reshape the schema the most:
// - 17 -> 18 drops the cloud-sync columns added in v1.6 and turns its soft deletes into real
//   deletes, so it's the one that can lose user data if it gets a row wrong.
// - 14 -> 18 runs the full chain from the last schema before v1.6. 14.json is hand-authored to
//   mirror exactly what Room generated back then (exportSchema was false at the time).
// runMigrationsAndValidate() always validates against the current compiled AppDatabase, so any
// leftover or missing column after the chain fails the test.
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate17To18_dropsSyncColumnsAndAppliesSoftDeletes() {
        val dbName = "migration-17-18-test-db"
        var db = helper.createDatabase(dbName, 17)

        // List 1 is active; list 2 had been deleted (soft delete) and list 3 was purged, which
        // leaves the AUTOINCREMENT counter at 3.
        insertList(db, id = 1, title = "Casa", deletedAt = null)
        insertList(db, id = 2, title = "Borrada", deletedAt = 7000)
        insertList(db, id = 3, title = "Purgada", deletedAt = null)
        db.execSQL("DELETE FROM task_lists WHERE id = 3")

        // Task 10 is active. Task 11 sits in the trash since t=5000. Task 12 belongs to the
        // deleted list.
        insertTask(db, id = 10, listId = 1, isDeleted = false, deletedAt = null)
        insertTask(db, id = 11, listId = 1, isDeleted = true, deletedAt = 5000)
        insertTask(db, id = 12, listId = 2, isDeleted = true, deletedAt = 7000)

        insertSubtask(db, id = 100, taskId = 10, deletedAt = null)   // active -> kept
        insertSubtask(db, id = 101, taskId = 10, deletedAt = 3000)   // deleted by the user -> gone
        insertSubtask(db, id = 102, taskId = 11, deletedAt = 5001)   // trashed with its task -> revived
        insertSubtask(db, id = 103, taskId = 11, deletedAt = 4000)   // deleted before the task -> gone
        insertSubtask(db, id = 104, taskId = 12, deletedAt = 7001)   // parent list deleted -> gone

        db.execSQL(
            "INSERT INTO reminders (id, title, description, dateTime, isCompleted, createdAt, isDeleted, " +
                "latitude, longitude, radius, locationName, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (200, 'Recuerdame', '', 1000, 0, 1000, 1, NULL, NULL, NULL, NULL, 'r-200', 1000, 1500, 0)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(dbName, 18, true, AppDatabase.MIGRATION_17_18)

        assertEquals(listOf(1L), ids(db, "task_lists"))
        assertEquals(listOf(10L, 11L), ids(db, "tasks"))
        assertEquals(listOf(100L, 102L), ids(db, "subtasks"))
        // Reminders in the trash stay in the trash.
        assertEquals(listOf(200L), ids(db, "reminders"))
        db.query("SELECT isDeleted FROM reminders WHERE id = 200").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        for (table in listOf("task_lists", "tasks", "subtasks", "reminders")) {
            val columns = columns(db, table)
            for (syncColumn in listOf("uuid", "updatedAt", "deletedAt", "dirty", "imagePath")) {
                assertFalse("$table.$syncColumn must be gone", syncColumn in columns)
            }
        }

        // The AUTOINCREMENT counter survives the table rebuild, so ids of deleted rows (which
        // alarms use as request codes) are never handed out again.
        db.execSQL(
            "INSERT INTO task_lists (title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color) " +
                "VALUES ('Nueva', 'ic_list', 0, 0, 0, 0, '#7F52FF')"
        )
        db.query("SELECT MAX(id) FROM task_lists").use {
            assertTrue(it.moveToFirst())
            assertEquals(4L, it.getLong(0))
        }

        db.close()
    }

    @Test
    fun migrate14To18_keepsDataThroughTheWholeChain() {
        val dbName = "migration-14-18-test-db"
        var db = helper.createDatabase(dbName, 14)

        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color) " +
                "VALUES (1, 'Casa', 'ic_list', 5000, 0, 0, 0, '#7F52FF')"
        )
        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color) " +
                "VALUES (2, 'Trabajo', 'ic_list', 5000, 1, 1, 0, '#7F52FF')"
        )
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate) " +
                "VALUES (10, 1, 'Pagar la luz', '', 0, '', 0, NULL, 0, 'NONE', 0, 0, NULL, 0)"
        )
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate) " +
                "VALUES (11, 1, 'Comprar leche', '', 0, '', 9999, NULL, 1, 'NONE', 1, 0, NULL, 0)"
        )

        // Subtasks of two different parent tasks, to verify orderIndex is backfilled per task
        // (grouped by taskId ASC, id ASC) instead of as one global sequence.
        db.execSQL("INSERT INTO subtasks (id, taskId, title, completed, dueDate) VALUES (100, 10, 'Sub A1', 0, NULL)")
        db.execSQL("INSERT INTO subtasks (id, taskId, title, completed, dueDate) VALUES (101, 10, 'Sub A2', 0, NULL)")
        db.execSQL("INSERT INTO subtasks (id, taskId, title, completed, dueDate) VALUES (102, 11, 'Sub B1', 0, NULL)")

        db.execSQL(
            "INSERT INTO reminders (id, title, description, dateTime, isCompleted, createdAt, isDeleted, latitude, longitude, radius, locationName) " +
                "VALUES (200, 'Recuerdame', '', 1000, 0, 1000, 0, NULL, NULL, NULL, NULL)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(
            dbName,
            18,
            true,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17,
            AppDatabase.MIGRATION_17_18
        )

        assertEquals(listOf(1L, 2L), ids(db, "task_lists"))
        assertEquals(listOf(10L, 11L), ids(db, "tasks"))
        assertEquals(listOf(200L), ids(db, "reminders"))

        // Task 11 was already in the trash before v1.6 and stays there, subtasks included.
        db.query("SELECT isDeleted FROM tasks WHERE id = 11").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }

        val orderById = mutableMapOf<Long, Int>()
        db.query("SELECT id, orderIndex, createdAt FROM subtasks ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                orderById[cursor.getLong(0)] = cursor.getInt(1)
                assertTrue("subtasks.createdAt must be backfilled", cursor.getLong(2) > 0)
            }
        }
        assertEquals(mapOf(100L to 0, 101L to 1, 102L to 0), orderById)

        db.close()
    }

    private fun insertList(db: SupportSQLiteDatabase, id: Long, title: String, deletedAt: Long?) {
        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color, " +
                "uuid, updatedAt, deletedAt, dirty) VALUES (?, ?, 'ic_list', 1000, 0, 0, 0, '#7F52FF', ?, 1000, ?, 0)",
            arrayOf<Any?>(id, title, "list-$id", deletedAt)
        )
    }

    private fun insertTask(db: SupportSQLiteDatabase, id: Long, listId: Long, isDeleted: Boolean, deletedAt: Long?) {
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, " +
                "recurrence, isDeleted, priority, imageUri, timeEstimate, uuid, updatedAt, deletedAt, dirty, imagePath) " +
                "VALUES (?, ?, 'Tarea', '', 0, '', 1000, NULL, 0, 'NONE', ?, 0, NULL, 0, ?, 1000, ?, 0, NULL)",
            arrayOf<Any?>(id, listId, if (isDeleted) 1 else 0, "task-$id", deletedAt)
        )
    }

    private fun insertSubtask(db: SupportSQLiteDatabase, id: Long, taskId: Long, deletedAt: Long?) {
        db.execSQL(
            "INSERT INTO subtasks (id, taskId, title, completed, dueDate, orderIndex, createdAt, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (?, ?, 'Subtarea', 0, NULL, 0, 1000, ?, 1000, ?, 0)",
            arrayOf<Any?>(id, taskId, "subtask-$id", deletedAt)
        )
    }

    private fun ids(db: SupportSQLiteDatabase, table: String): List<Long> =
        db.query("SELECT id FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }
}
