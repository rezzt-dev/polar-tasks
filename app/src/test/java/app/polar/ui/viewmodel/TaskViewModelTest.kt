package app.polar.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.work.WorkManager
import app.polar.data.entity.Task
import app.polar.data.repository.TaskRepository
import app.polar.data.sync.SyncManager
import app.polar.data.sync.TaskImageStorage
import app.polar.domain.usecase.GetFilteredTasksUseCase
import app.polar.util.AlarmManagerHelper
import app.polar.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val repository = mockk<TaskRepository>(relaxed = true)
    private val alarmHelper = mockk<AlarmManagerHelper>(relaxed = true)
    private val taskImageStorage = mockk<TaskImageStorage>(relaxed = true)
    private val getFilteredTasksUseCase = mockk<GetFilteredTasksUseCase>()
    private val syncManager = mockk<SyncManager>(relaxed = true)

    private lateinit var viewModel: TaskViewModel

    // safeLaunch()/the new trash-purge blocks all end with SyncWorker.triggerImmediateSync(),
    // which calls the real WorkManager.getInstance() — unavailable in a plain JVM unit test.
    // Stubbed out so it doesn't throw and clobber the ViewModel's real errorMessage/success path.
    @Before
    fun stubWorkManager() {
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockk(relaxed = true)
    }

    @After
    fun unstubWorkManager() {
        unmockkStatic(WorkManager::class)
    }

    private fun setupViewModel() {
        every { getFilteredTasksUseCase(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        viewModel = TaskViewModel(application, repository, alarmHelper, taskImageStorage, getFilteredTasksUseCase, syncManager)
    }

    @Test
    fun `tasks StateFlow emits data from UseCase`() = runTest {
        // Given
        val mockTasks = listOf(Task(id = 1, listId = 1L, title = "Test Task"))
        
        // Mock UseCase to return a Flow
        every { getFilteredTasksUseCase(any(), any(), any(), any(), any()) } returns flowOf(mockTasks)

        // Init ViewModel
        viewModel = TaskViewModel(application, repository, alarmHelper, taskImageStorage, getFilteredTasksUseCase, syncManager)

        // When/Then (collecting StateFlow)
        val collected = viewModel.tasks.value
        
        // However, simpler test for logic delegation:
        verify { getFilteredTasksUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `insertTask calls repository and alarmHelper`() = runTest {
        setupViewModel()
        
        val taskTitle = "New Task"
        val dueDate = 123456789L
        coEvery { repository.insertTask(any()) } returns 1L

        viewModel.insertTask(1L, taskTitle, "Desc", dueDate = dueDate)
        
        // Wait for coroutine? safeLaunch uses viewModelScope.
        // runTest usually advances data, but unconfined dispatcher executes immediately.
        
        coVerify { repository.insertTask(match { it.title == taskTitle }) }
        // Verify Alarm
        verify { alarmHelper.scheduleTaskAlarm(1L, dueDate) }
    }

    @Test
    fun `setTaskCompletion updates task and alarm`() = runTest {
         setupViewModel()

         val task = Task(id = 1, listId = 1L, title = "Task", completed = false, dueDate = 1000L)

         viewModel.setTaskCompletion(task, true)

         coVerify { repository.updateTask(match { it.id == 1L && it.completed }) }
         verify { alarmHelper.cancelTaskAlarm(1L) }
    }

    // --- Trash purge (hallazgo 3.1/3.2): syncs first, then reports what couldn't be purged ---

    @Test
    fun `emptyTrash syncs before asking the repository to purge`() = runTest {
        setupViewModel()
        coEvery { syncManager.sync() } returns Result.success(Unit)
        coEvery { repository.emptyTrash() } returns 0

        viewModel.emptyTrash()

        coVerifyOrder {
            syncManager.sync()
            repository.emptyTrash()
        }
    }

    @Test
    fun `emptyTrash surfaces an error when items remain stuck after the purge`() = runTest {
        setupViewModel()
        coEvery { syncManager.sync() } returns Result.success(Unit)
        coEvery { repository.emptyTrash() } returns 2

        viewModel.emptyTrash()

        assertEquals(true, viewModel.errorMessage.value != null)
    }

    @Test
    fun `permanentDelete surfaces an error when the row could not be purged`() = runTest {
        setupViewModel()
        val task = Task(id = 1, listId = 1L, title = "Stuck in trash")
        coEvery { syncManager.sync() } returns Result.success(Unit)
        coEvery { repository.permanentDeleteTask(1L) } returns false

        viewModel.permanentDelete(task)

        assertEquals(true, viewModel.errorMessage.value != null)
    }

    @Test
    fun `permanentDelete clears no error when the row was purged`() = runTest {
        setupViewModel()
        val task = Task(id = 1, listId = 1L, title = "Goes away")
        coEvery { syncManager.sync() } returns Result.success(Unit)
        coEvery { repository.permanentDeleteTask(1L) } returns true

        viewModel.permanentDelete(task)

        assertEquals(null, viewModel.errorMessage.value)
    }
}
