package app.polar.data.sync

import app.polar.data.dao.ReminderDao
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.util.AlarmManagerHelper
import app.polar.util.FakePostgrestRouter
import app.polar.util.MainDispatcherRule
import app.polar.util.fakeSupabaseClient
import app.polar.util.signInWithFakeSession
import io.ktor.http.HttpMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.just
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Integration tests for SyncManager's orchestration (push -> pull, and pushAllOverwrite), driven
// through a real SupabaseClient wired to a fake HTTP boundary (see app/src/test/.../FakeSupabase.kt)
// rather than through the pure resolvePushOutcome/resolvePullAction functions MergeResolverTest
// already covers in isolation. This is the "sin ningun test para SyncManager.push()/pull()/sync()"
// gap from agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 6 / Fase 7.1.
//
// DAOs stay mockk (same style as TaskRepositoryTest) since Room's real SQLite behavior isn't what's
// under test here — only SyncManager's own decisions (what it sends, what it does with what comes
// back) are. The @Before block stubs the "nothing dirty, nothing to pull" steady state shared by
// every test; each test overrides only the calls its scenario actually exercises.
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userId = "6f1a8b2e-0000-4a1a-9a1a-000000000001"

    private val taskListDao = mockk<TaskListDao>()
    private val taskDao = mockk<TaskDao>()
    private val subtaskDao = mockk<SubtaskDao>()
    private val reminderDao = mockk<ReminderDao>()
    private val syncPrefs = mockk<SyncPrefs>(relaxed = true)
    private val alarmHelper = mockk<AlarmManagerHelper>(relaxed = true)
    private val router = FakePostgrestRouter()

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() = runTest {
        // pushAllOverwrite()'s catch block logs via android.util.Log.e, which isn't mocked by
        // default in a plain JVM unit test (same shape as the WorkManager stub TaskViewModelTest
        // already needs for its own real-Android-API calls).
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0

        // Steady state: no dirty rows anywhere, no confirmed tombstones to check for a remote
        // purge, nothing new since the last pull on any of the 4 tables. Individual tests
        // override exactly the calls their scenario needs to differ from this.
        coEvery { taskListDao.getDirtyTaskLists() } returns emptyList()
        coEvery { taskDao.getDirtyTasks() } returns emptyList()
        coEvery { subtaskDao.getDirtySubtasks() } returns emptyList()
        coEvery { reminderDao.getDirtyReminders() } returns emptyList()
        coEvery { taskDao.getConfirmedTrashedTasksSnapshot() } returns emptyList()
        coEvery { reminderDao.getConfirmedTrashedRemindersSnapshot() } returns emptyList()

        val supabaseClient = fakeSupabaseClient(router.engine())
        supabaseClient.signInWithFakeSession(userId)
        syncManager = SyncManager(supabaseClient, taskListDao, taskDao, subtaskDao, reminderDao, syncPrefs, alarmHelper)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    // --- push: successful (WON) ---

    @Test
    fun `sync pushes a dirty task list and clears its dirty flag when the server echoes the same updatedAt`() = runTest {
        val local = TaskList(id = 1, title = "Casa", uuid = "list-uuid-1", updatedAt = 1000L, dirty = true)
        coEvery { taskListDao.getDirtyTaskLists() } returns listOf(local)
        coEvery { taskListDao.updateAll(any()) } just Runs
        router.on(
            "task_lists", HttpMethod.Post,
            """[{"id":"list-uuid-1","user_id":"$userId","title":"Casa","icon":"ic_list","color":"#7F52FF","order_index":0,"home_order_index":0,"is_dependency_chain":false,"is_deleted":false,"deleted_at":null,"created_at":1000,"updated_at":1000}]"""
        )

        val result = syncManager.sync()

        assertTrue(result.isSuccess)
        coVerify {
            taskListDao.updateAll(match { it.size == 1 && it[0].id == 1L && !it[0].dirty })
        }
    }

    // --- push: lost conflict (LOST) ---

    @Test
    fun `sync overwrites a local task with the servers winner and counts the lost conflict when the server returns a newer updatedAt`() = runTest {
        val localList = TaskList(id = 2, title = "Lista", uuid = "list-uuid-2")
        val localTask = Task(id = 5, listId = 2, title = "Edicion local", uuid = "task-uuid-1", updatedAt = 1000L, dirty = true)
        coEvery { taskDao.getDirtyTasks() } returns listOf(localTask)
        coEvery { taskListDao.getListById(2) } returns localList
        coEvery { taskListDao.getByUuid("list-uuid-2") } returns localList
        coEvery { taskDao.updateAll(any()) } just Runs
        router.on(
            "tasks", HttpMethod.Post,
            """[{"id":"task-uuid-1","user_id":"$userId","list_id":"list-uuid-2","title":"Edicion en otro dispositivo","description":"","completed":false,"tags":[],"due_date":null,"order_index":0,"recurrence":"NONE","priority":0,"image_path":null,"time_estimate":0,"is_deleted":false,"deleted_at":null,"created_at":900,"updated_at":2000}]"""
        )

        val result = syncManager.sync()

        assertTrue(result.isSuccess)
        coVerify {
            taskDao.updateAll(match { it.size == 1 && it[0].id == 5L && it[0].title == "Edicion en otro dispositivo" && !it[0].dirty })
        }
        // Relaxed syncPrefs returns 0 for the getter by default, so `+= 1` resolves to writing 1.
        io.mockk.verify { syncPrefs.lostConflictsCount = 1 }
    }

    // --- pull: insert ---

    @Test
    fun `sync inserts a brand-new remote task list that has no local counterpart`() = runTest {
        coEvery { taskListDao.getByUuid("list-uuid-new") } returns null
        coEvery { taskListDao.insert(any()) } returns 42L
        router.on(
            "task_lists", HttpMethod.Get,
            """[{"id":"list-uuid-new","user_id":"$userId","title":"Nueva desde otra app","icon":"ic_list","color":"#7F52FF","order_index":0,"home_order_index":0,"is_dependency_chain":false,"is_deleted":false,"deleted_at":null,"created_at":1000,"updated_at":1000}]"""
        )

        val result = syncManager.sync()

        assertTrue(result.isSuccess)
        coVerify {
            taskListDao.insert(match { it.uuid == "list-uuid-new" && it.title == "Nueva desde otra app" })
        }
    }

    // --- pull: update ---

    @Test
    fun `sync overwrites a local task list when the remote version is strictly newer`() = runTest {
        val local = TaskList(id = 3, title = "Titulo viejo", uuid = "list-uuid-3", updatedAt = 1000L, dirty = false)
        coEvery { taskListDao.getByUuid("list-uuid-3") } returns local
        coEvery { taskListDao.update(any()) } just Runs
        router.on(
            "task_lists", HttpMethod.Get,
            """[{"id":"list-uuid-3","user_id":"$userId","title":"Titulo nuevo","icon":"ic_list","color":"#7F52FF","order_index":0,"home_order_index":0,"is_dependency_chain":false,"is_deleted":false,"deleted_at":null,"created_at":1000,"updated_at":2000}]"""
        )

        val result = syncManager.sync()

        assertTrue(result.isSuccess)
        coVerify {
            taskListDao.update(match { it.id == 3L && it.title == "Titulo nuevo" })
        }
    }

    // --- pull: parent not found locally yet (out-of-order arrival) ---

    @Test
    fun `sync skips a remote task whose parent list has not synced locally yet`() = runTest {
        coEvery { taskListDao.getByUuid("list-uuid-missing") } returns null
        router.on(
            "tasks", HttpMethod.Get,
            """[{"id":"task-uuid-orphan","user_id":"$userId","list_id":"list-uuid-missing","title":"Huerfana","description":"","completed":false,"tags":[],"due_date":null,"order_index":0,"recurrence":"NONE","priority":0,"image_path":null,"time_estimate":0,"is_deleted":false,"deleted_at":null,"created_at":1000,"updated_at":1000}]"""
        )

        val result = syncManager.sync()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { taskDao.insert(any()) }
        coVerify(exactly = 0) { taskDao.update(any()) }
    }

    // --- pushAllOverwrite: full round-trip ---

    @Test
    fun `pushAllOverwrite force-touches every local row, uploads it, and tombstones a remote orphan`() = runTest {
        val local = TaskList(id = 1, title = "Casa", uuid = "list-uuid-1", updatedAt = 500L, dirty = false)
        val touched = local.touched()
        coEvery { taskListDao.getAllTaskListsIncludingDeletedSnapshot() } returns listOf(local)
        coEvery { taskDao.getAllTasksSnapshot() } returns emptyList()
        coEvery { subtaskDao.getAllSubtasksSnapshot() } returns emptyList()
        coEvery { reminderDao.getAllRemindersSnapshot() } returns emptyList()
        coEvery { taskListDao.updateAll(any()) } just Runs
        coEvery { taskDao.updateAll(emptyList()) } just Runs
        // Simulates the touch from touchAllLocalRows() having "persisted": the subsequent push
        // step reads it back as dirty via the normal getDirtyTaskLists() query.
        coEvery { taskListDao.getDirtyTaskLists() } returns listOf(touched)
        router.on(
            "task_lists", HttpMethod.Post,
            """[{"id":"list-uuid-1","user_id":"$userId","title":"Casa","icon":"ic_list","color":"#7F52FF","order_index":0,"home_order_index":0,"is_dependency_chain":false,"is_deleted":false,"deleted_at":null,"created_at":500,"updated_at":${touched.updatedAt}}]"""
        )
        // deleteRemoteOrphans(): the remote has this device's list plus one this device has never
        // seen — the orphan must get soft-deleted remotely (a PATCH), the known one left alone.
        router.on(
            "task_lists", HttpMethod.Get,
            """[
                {"id":"list-uuid-1","user_id":"$userId","title":"Casa","icon":"ic_list","color":"#7F52FF","order_index":0,"home_order_index":0,"is_dependency_chain":false,"is_deleted":false,"deleted_at":null,"created_at":500,"updated_at":${touched.updatedAt}},
                {"id":"list-uuid-orphan","user_id":"$userId","title":"Fantasma","icon":"ic_list","color":"#7F52FF","order_index":0,"home_order_index":0,"is_dependency_chain":false,"is_deleted":false,"deleted_at":null,"created_at":500,"updated_at":500}
            ]"""
        )

        val result = syncManager.pushAllOverwrite()

        assertTrue(result.isSuccess)
        coVerify {
            // touchAllLocalRows()
            taskListDao.updateAll(match { it.size == 1 && it[0].dirty })
        }
        coVerify {
            // WON branch of the push step
            taskListDao.updateAll(match { it.size == 1 && !it[0].dirty })
        }
        val orphanPatch = router.requests.firstOrNull {
            it.method == HttpMethod.Patch && it.url.encodedPath.endsWith("/task_lists")
        }
        assertTrue("expected a PATCH to task_lists tombstoning the orphan", orphanPatch != null)
        // deleteOrphans() filters by `eq("id", orphanId)` — the target row is identified in the
        // URL's query params, not the request body (which only carries the tombstone fields).
        assertTrue(orphanPatch!!.url.toString().contains("list-uuid-orphan"))
    }
}
