package app.polar.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.polar.data.entity.Task
import app.polar.data.repository.TaskRepository
import app.polar.domain.usecase.GetFilteredTasksUseCase
import app.polar.util.AlarmManagerHelper
import app.polar.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
    fun `tasks StateFlow reads from the filtered-tasks use case`() = runTest {
        setupViewModel()

        verify { getFilteredTasksUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `insertTask calls repository and alarmHelper`() = runTest {
        setupViewModel()

        val taskTitle = "New Task"
        val dueDate = 123456789L
        coEvery { repository.insertTask(any()) } returns 1L

        viewModel.insertTask(1L, taskTitle, "Desc", dueDate = dueDate)

        coVerify { repository.insertTask(match { it.title == taskTitle }) }
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

    // --- Trash: the viewmodel is a thin delegation layer over the repository, no sync step
    // in between (offline: permanentDeleteTask/emptyTrash are unconditional physical deletes) ---

    @Test
    fun `permanentDelete delegates to the repository`() = runTest {
        setupViewModel()
        val task = Task(id = 1, listId = 1L, title = "Goes away")
        coEvery { repository.permanentDeleteTask(1L) } returns Unit

        viewModel.permanentDelete(task)

        coVerify { repository.permanentDeleteTask(1L) }
    }

    @Test
    fun `emptyTrash delegates to the repository`() = runTest {
        setupViewModel()
        coEvery { repository.emptyTrash() } returns Unit

        viewModel.emptyTrash()

        coVerify { repository.emptyTrash() }
    }
}
