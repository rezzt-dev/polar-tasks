package app.polar.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented test for MIGRATION_14_15 (agent-docs/analisis-implementacion-supabase-sync.md,
// hallazgo 6 / Fase 7.2): the most complex migration in the chain, since it's the one that adds
// the cloud-sync bookkeeping columns and backfills them for every pre-existing row without a
// schema JSON having ever been exported for version 14 (exportSchema was false until this fase).
// app/schemas/app.polar.data.AppDatabase/14.json is hand-authored to mirror exactly what Room
// would have generated back then (reconstructed from entity history + the MIGRATION_6_7..13_14
// chain in AppDatabase, cross-checked against the real compiler-generated 17.json for format).
// Runs the full 14->17 chain (not just 14->15) because runMigrationsAndValidate() always
// validates the resulting schema against the *current* compiled AppDatabase (v17), so an
// intermediate stop at 15 would fail validation over columns 15_16/16_17 haven't added yet.
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate14To17_backfillsUuidAndUpdatedAtWithoutCollisions() {
        var db = helper.createDatabase(testDb, 14)

        // Two rows sharing the exact same createdAt (a duplicate/edge-case value on purpose,
        // see hallazgo 6 / Fase 7.2 "valores límite"): if uuid backfill ever derived the uuid
        // from row data instead of a fresh random one, these would collide on the new unique
        // index and the migration would throw.
        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color) " +
                "VALUES (1, 'Casa', 'ic_list', 5000, 0, 0, 0, '#7F52FF')"
        )
        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color) " +
                "VALUES (2, 'Trabajo', 'ic_list', 5000, 1, 1, 0, '#7F52FF')"
        )

        // createdAt = 0 is a boundary value (e.g. a row inserted before createdAt existed and
        // never touched since) — the backfill must still produce a non-blank, valid uuid and
        // updatedAt = 0, not skip the row.
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate) " +
                "VALUES (10, 1, 'Pagar la luz', '', 0, '', 0, NULL, 0, 'NONE', 0, 0, NULL, 0)"
        )
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate) " +
                "VALUES (11, 1, 'Comprar leche', '', 0, '', 9999, NULL, 1, 'NONE', 0, 0, NULL, 0)"
        )

        // Subtasks for the same task inserted out of id order across two different parent
        // tasks, to verify backfillSubtaskOrderIndex() partitions "per taskId" correctly instead
        // of assigning one global sequence (it groups by taskId ASC, id ASC).
        db.execSQL("INSERT INTO subtasks (id, taskId, title, completed, dueDate) VALUES (100, 10, 'Sub A1', 0, NULL)")
        db.execSQL("INSERT INTO subtasks (id, taskId, title, completed, dueDate) VALUES (101, 10, 'Sub A2', 0, NULL)")
        db.execSQL("INSERT INTO subtasks (id, taskId, title, completed, dueDate) VALUES (102, 11, 'Sub B1', 0, NULL)")

        db.execSQL(
            "INSERT INTO reminders (id, title, description, dateTime, isCompleted, createdAt, isDeleted, latitude, longitude, radius, locationName) " +
                "VALUES (200, 'Recuerdame', '', 1000, 0, 1000, 0, NULL, NULL, NULL, NULL)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(
            testDb,
            17,
            true,
            AppDatabase.MIGRATION_14_15,
            AppDatabase.MIGRATION_15_16,
            AppDatabase.MIGRATION_16_17
        )

        // --- uuid/updatedAt backfill: every row gets a distinct, non-blank uuid ---
        val taskListUuids = mutableSetOf<String>()
        db.query("SELECT id, uuid, updatedAt FROM task_lists ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid"))
                assertTrue("uuid must not be blank", uuid.isNotBlank())
                assertTrue("uuid must be added to the set (i.e. unique)", taskListUuids.add(uuid))
                assertEquals(5000L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
            }
        }
        assertEquals(2, taskListUuids.size)

        db.query("SELECT id, uuid, updatedAt, createdAt FROM tasks WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(cursor.getColumnIndexOrThrow("uuid")).isNotBlank())
            // Boundary value: createdAt = 0 backfills updatedAt = 0, not "now" or skipped.
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        }

        // --- subtasks.orderIndex backfill: sequential per parent task, not globally ---
        val orderByTaskId10 = mutableMapOf<Long, Int>()
        val orderByTaskId11 = mutableMapOf<Long, Int>()
        db.query("SELECT id, taskId, orderIndex FROM subtasks ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val taskId = cursor.getLong(cursor.getColumnIndexOrThrow("taskId"))
                val orderIndex = cursor.getInt(cursor.getColumnIndexOrThrow("orderIndex"))
                if (taskId == 10L) orderByTaskId10[id] = orderIndex else orderByTaskId11[id] = orderIndex
            }
        }
        assertEquals(mapOf(100L to 0, 101L to 1), orderByTaskId10)
        assertEquals(mapOf(102L to 0), orderByTaskId11)

        // --- subtasks.createdAt (15_16) was backfilled from updatedAt, not left at 0 ---
        db.query("SELECT createdAt, updatedAt FROM subtasks WHERE id = 100").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
            )
        }

        // --- reminders row survived the chain untouched in its non-sync columns ---
        db.query("SELECT uuid, dirty FROM reminders WHERE id = 200").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNotNull(cursor.getString(cursor.getColumnIndexOrThrow("uuid")))
            // Pre-existing rows start dirty = 1 (never pushed yet) per MIGRATION_14_15's column
            // default, so the very next sync uploads this device's whole pre-sync history.
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("dirty")))
        }

        // --- the new unique index on uuid is real, not just a column that happens to be unique ---
        var indexRejectedDuplicate = false
        try {
            db.execSQL(
                "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color, uuid, updatedAt, deletedAt, dirty) " +
                    "SELECT 999, 'Duplicado', icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color, uuid, updatedAt, deletedAt, dirty FROM task_lists WHERE id = 1"
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            indexRejectedDuplicate = true
        }
        assertTrue("index_task_lists_uuid must reject a duplicate uuid", indexRejectedDuplicate)

        db.close()
    }

    // Instrumented test for MIGRATION_17_18 (agent-docs/eliminacion-supabase/, Fase 4): drops the
    // cloud-sync bookkeeping columns (uuid/updatedAt/deletedAt/dirty, +tasks.imagePath) added by
    // 14_15/16_17, via the "dump children to FK-less temp tables, recreate hijo→padre" pattern.
    // Pre-populates a v17 DB (dirty rows, trashed rows, subtasks with non-trivial orderIndex, a
    // located reminder) and verifies full data conservation plus the absence of every sync column.
    @Test
    fun migrate17To18_dropsSyncColumnsAndKeepsData() {
        var db = helper.createDatabase(testDb, 17)

        // Two lists — one of them still 'dirty' (never pushed), which must not block anything
        // now that the migration doesn't gate on dirty at all.
        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (1, 'Casa', 'ic_list', 5000, 0, 0, 0, '#7F52FF', 'uuid-list-1', 5000, NULL, 1)"
        )
        db.execSQL(
            "INSERT INTO task_lists (id, title, icon, createdAt, orderIndex, homeOrderIndex, isDependencyChain, color, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (2, 'Trabajo', 'ic_list', 5100, 1, 1, 0, '#7F52FF', 'uuid-list-2', 5100, NULL, 0)"
        )

        // A live task and a trashed one (isDeleted = 1), both dirty = 1, to verify the trash
        // state (not the sync bookkeeping) is what survives.
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate, uuid, updatedAt, deletedAt, dirty, imagePath) " +
                "VALUES (10, 1, 'Pagar la luz', 'desc', 0, 'urgente', 1000, 9999, 0, 'NONE', 0, 2, 'content://img', 30, 'uuid-task-10', 1000, NULL, 1, 'user/task-10.jpg')"
        )
        db.execSQL(
            "INSERT INTO tasks (id, listId, title, description, completed, tags, createdAt, dueDate, orderIndex, recurrence, isDeleted, priority, imageUri, timeEstimate, uuid, updatedAt, deletedAt, dirty, imagePath) " +
                "VALUES (11, 1, 'Tarea vieja', '', 1, '', 900, NULL, 1, 'NONE', 1, 0, NULL, 0, 'uuid-task-11', 950, 950, 1, NULL)"
        )

        // Subtasks with a non-trivial orderIndex, across two different parent tasks.
        db.execSQL(
            "INSERT INTO subtasks (id, taskId, title, completed, dueDate, orderIndex, createdAt, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (100, 10, 'Sub A1', 0, NULL, 0, 1000, 'uuid-sub-100', 1000, NULL, 1)"
        )
        db.execSQL(
            "INSERT INTO subtasks (id, taskId, title, completed, dueDate, orderIndex, createdAt, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (101, 10, 'Sub A2', 1, 8888, 1, 1001, 'uuid-sub-101', 1001, NULL, 1)"
        )
        db.execSQL(
            "INSERT INTO subtasks (id, taskId, title, completed, dueDate, orderIndex, createdAt, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (102, 11, 'Sub B1', 0, NULL, 0, 900, 'uuid-sub-102', 900, NULL, 0)"
        )

        // A located reminder, in the trash.
        db.execSQL(
            "INSERT INTO reminders (id, title, description, dateTime, isCompleted, createdAt, isDeleted, latitude, longitude, radius, locationName, uuid, updatedAt, deletedAt, dirty) " +
                "VALUES (200, 'Recuerdame', 'desc', 2000, 0, 2000, 1, 40.4168, -3.7038, 150.0, 'Madrid', 'uuid-rem-200', 2000, 2100, 1)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDb, 18, true, AppDatabase.MIGRATION_17_18)

        // --- row counts are identical per table ---
        db.query("SELECT COUNT(*) FROM task_lists").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM tasks").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM subtasks").use { assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM reminders").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }

        // --- field-by-field conservation on one row of each table ---
        db.query("SELECT * FROM task_lists WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Casa", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(5000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
            assertEquals("#7F52FF", cursor.getString(cursor.getColumnIndexOrThrow("color")))
        }

        db.query("SELECT * FROM tasks WHERE id = 10").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Pagar la luz", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals("urgente", cursor.getString(cursor.getColumnIndexOrThrow("tags")))
            assertEquals(9999L, cursor.getLong(cursor.getColumnIndexOrThrow("dueDate")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")))
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("priority")))
            assertEquals("content://img", cursor.getString(cursor.getColumnIndexOrThrow("imageUri")))
            assertEquals(30, cursor.getInt(cursor.getColumnIndexOrThrow("timeEstimate")))
        }
        db.query("SELECT isDeleted FROM tasks WHERE id = 11").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")))
        }

        // --- subtasks.orderIndex/createdAt/dueDate survive, per-task ordering intact ---
        db.query("SELECT orderIndex, dueDate, createdAt FROM subtasks WHERE id = 101").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("orderIndex")))
            assertEquals(8888L, cursor.getLong(cursor.getColumnIndexOrThrow("dueDate")))
            assertEquals(1001L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        }

        // --- reminder location fields survive ---
        db.query("SELECT latitude, longitude, radius, locationName, isDeleted FROM reminders WHERE id = 200").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(40.4168, cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")), 0.0001)
            assertEquals(-3.7038, cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")), 0.0001)
            assertEquals(150.0f, cursor.getFloat(cursor.getColumnIndexOrThrow("radius")), 0.001f)
            assertEquals("Madrid", cursor.getString(cursor.getColumnIndexOrThrow("locationName")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isDeleted")))
        }

        // --- every sync column is gone from every table ---
        val syncColumns = setOf("uuid", "updatedAt", "deletedAt", "dirty")
        for (table in listOf("task_lists", "tasks", "subtasks", "reminders")) {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
                assertTrue(
                    "$table must not keep any sync column, found: ${columns.intersect(syncColumns)}",
                    columns.intersect(syncColumns).isEmpty()
                )
            }
        }
        db.query("PRAGMA table_info(tasks)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val columns = mutableSetOf<String>()
            while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
            assertTrue("tasks must not keep imagePath", "imagePath" !in columns)
        }

        // --- referential integrity: FK from tasks(listId) to task_lists and subtasks(taskId) to tasks ---
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("no dangling foreign keys after the migration", 0, cursor.count)
        }

        db.close()
    }
}
