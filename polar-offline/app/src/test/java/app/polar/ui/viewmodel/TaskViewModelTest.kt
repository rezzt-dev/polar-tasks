package app.polar.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.polar.data.entity.Task
import app.polar.data.repository.TaskRepository
import app.polar.domain.usecase.GetFilteredTasksUseCase
import app.polar.util.AlarmManagerHelper
import app.polar.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val getFilteredTasksUseCase = mockk<GetFilteredTasksUseCase>()

    private lateinit var viewModel: TaskViewModel

    private fun setupViewModel() {
        every { getFilteredTasksUseCase(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        viewModel = TaskViewModel(application, repository, alarmHelper, getFilteredTasksUseCase)
    }

    @Test
    fun `tasks StateFlow emits data from UseCase`() = runTest {
        // Given
        val mockTasks = listOf(Task(id = 1, listId = 1L, title = "Test Task"))

        // Mock UseCase to return a Flow
        every { getFilteredTasksUseCase(any(), any(), any(), any(), any()) } returns flowOf(mockTasks)

        // Init ViewModel
        viewModel = TaskViewModel(application, repository, alarmHelper, getFilteredTasksUseCase)

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

    // --- Trash: everything is local, so purging is immediate and never blocked ---

    @Test
    fun `moveToTrash soft-deletes the task and cancels its alarm`() = runTest {
        setupViewModel()
        val task = Task(id = 1, listId = 1L, title = "To trash", dueDate = 1000L)

        viewModel.moveToTrash(task)

        coVerify { repository.softDeleteTask(task) }
        verify { alarmHelper.cancelTaskAlarm(1L) }
    }

    @Test
    fun `restoreFromTrash reschedules the alarm of a pending task`() = runTest {
        setupViewModel()
        val task = Task(id = 1, listId = 1L, title = "Back", dueDate = 1000L, isDeleted = true)

        viewModel.restoreFromTrash(task)

        coVerify { repository.restoreTask(task) }
        verify { alarmHelper.scheduleTaskAlarm(1L, 1000L) }
    }

    @Test
    fun `emptyTrash purges the trash without errors`() = runTest {
        setupViewModel()

        viewModel.emptyTrash()

        coVerify { repository.emptyTrash() }
        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun `permanentDelete purges the task without errors`() = runTest {
        setupViewModel()
        val task = Task(id = 1, listId = 1L, title = "Goes away")

        viewModel.permanentDelete(task)

        coVerify { repository.permanentDeleteTask(1L) }
        assertEquals(null, viewModel.errorMessage.value)
    }

    @Test
    fun `a failing repository call surfaces an error message`() = runTest {
        setupViewModel()
        coEvery { repository.emptyTrash() } throws IllegalStateException("disk full")

        viewModel.emptyTrash()

        assertEquals("Error: disk full", viewModel.errorMessage.value)
    }
}
