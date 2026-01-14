package app.polar.data.repository

import androidx.lifecycle.MutableLiveData
import app.polar.data.dao.TaskDao
import app.polar.data.entity.Task
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRepositoryTest {
    
    private val taskDao = mockk<TaskDao>()
    private val repository = TaskRepository(taskDao)

    @Test
    fun `getAllTasksFlow returns flow from dao`() = runTest {
        val tasks = listOf(Task(title = "Test Task"))
        every { taskDao.getAllTasksFlow() } returns flowOf(tasks)

        val result = repository.getAllTasksFlow()
        
        result.collect {
            assertEquals(tasks, it)
        }
        verify { taskDao.getAllTasksFlow() }
    }

    @Test
    fun `getTasksForListFlow returns flow from dao`() = runTest {
        val listId = 1L
        val tasks = listOf(Task(listId = listId, title = "List Task"))
        every { taskDao.getTasksForListFlow(listId) } returns flowOf(tasks)

        val result = repository.getTasksForListFlow(listId)

        result.collect {
            assertEquals(tasks, it)
        }
        verify { taskDao.getTasksForListFlow(listId) }
    }

    @Test
    fun `insertTask calls dao insert`() = runTest {
        val task = Task(title = "New Task")
        coEvery { taskDao.insertTask(task) } returns 1L

        val id = repository.insertTask(task)

        assertEquals(1L, id)
        coVerify { taskDao.insertTask(task) }
    }

    @Test
    fun `updateTask calls dao update`() = runTest {
        val task = Task(id = 1, title = "Updated")
        coEvery { taskDao.updateTask(task) } returns Unit

        repository.updateTask(task)

        coVerify { taskDao.updateTask(task) }
    }
    
    @Test
    fun `deleteTask calls dao delete`() = runTest {
        val task = Task(id = 1, title = "Delete me")
        coEvery { taskDao.deleteTask(task) } returns Unit

        repository.deleteTask(task)

        coVerify { taskDao.deleteTask(task) }
    }
}
