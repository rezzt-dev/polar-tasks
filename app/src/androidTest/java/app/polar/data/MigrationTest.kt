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
}
