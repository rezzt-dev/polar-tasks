package app.polar.data.repository

import androidx.lifecycle.MutableLiveData
import app.polar.data.dao.TaskDao
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
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
    private val taskListDao = mockk<app.polar.data.dao.TaskListDao>()
    private val subtaskDao = mockk<app.polar.data.dao.SubtaskDao>()

    init {
        every { taskListDao.getAllLists() } returns MutableLiveData()
    }

    private val repository = TaskRepository(taskListDao, taskDao, subtaskDao)

    @Test
    fun `getAllTasksFlow returns flow from dao`() = runTest {
        val tasks = listOf(Task(listId = 1L, title = "Test Task"))
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
        val task = Task(listId = 1L, title = "New Task")
        coEvery { taskDao.insert(task) } returns 1L

        val id = repository.insertTask(task)

        assertEquals(1L, id)
        coVerify { taskDao.insert(task) }
    }

    @Test
    fun `updateTask calls dao update`() = runTest {
        val task = Task(id = 1, listId = 1L, title = "Updated")
        coEvery { taskDao.update(task) } returns Unit

        repository.updateTask(task)

        coVerify { taskDao.update(task) }
    }

    // --- Trash: soft delete / restore / physical purge (no dirty/deletedAt tracking) ---

    @Test
    fun `softDeleteTask marks the task as deleted via dao update`() = runTest {
        val task = Task(id = 1, listId = 1L, title = "Delete me")
        coEvery { taskDao.update(any()) } returns Unit

        repository.softDeleteTask(task)

        coVerify { taskDao.update(match { it.id == 1L && it.isDeleted }) }
    }

    @Test
    fun `restoreTask marks the task as not deleted via dao update`() = runTest {
        val task = Task(id = 1, listId = 1L, title = "Restore me", isDeleted = true)
        coEvery { taskDao.update(any()) } returns Unit

        repository.restoreTask(task)

        coVerify { taskDao.update(match { it.id == 1L && !it.isDeleted }) }
    }

    @Test
    fun `permanentDeleteTask delegates to dao physical delete`() = runTest {
        coEvery { taskDao.permanentDelete(1L) } returns Unit

        repository.permanentDeleteTask(1L)

        coVerify { taskDao.permanentDelete(1L) }
    }

    @Test
    fun `emptyTrash delegates to dao physical purge of every deleted row`() = runTest {
        coEvery { taskDao.emptyTrash() } returns Unit

        repository.emptyTrash()

        coVerify { taskDao.emptyTrash() }
    }

    @Test
    fun `deleteTaskList delegates to dao physical delete (FK cascade removes its tasks)`() = runTest {
        val taskList = TaskList(id = 1, title = "List")
        coEvery { taskListDao.delete(taskList) } returns Unit

        repository.deleteTaskList(taskList)

        coVerify { taskListDao.delete(taskList) }
    }

    // --- replaceSubtasksForTask: diff-based replace, see doc 06 punto 3 ---

    @Test
    fun `replaceSubtasksForTask inserts a brand new subtask (id 0)`() = runTest {
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns emptyList()
        coEvery { subtaskDao.insert(any()) } returns 10L

        repository.replaceSubtasksForTask(1L, listOf(Subtask(id = 0, taskId = 0, title = "Nueva")))

        coVerify {
            subtaskDao.insert(match {
                it.taskId == 1L && it.title == "Nueva" && it.orderIndex == 0
            })
        }
    }

    @Test
    fun `replaceSubtasksForTask updates an existing subtask only when a field actually changed`() = runTest {
        val existing = Subtask(id = 5, taskId = 1L, title = "Original", completed = false, orderIndex = 0)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(existing)
        coEvery { subtaskDao.update(any()) } returns Unit

        repository.replaceSubtasksForTask(1L, listOf(existing.copy(title = "Renombrada")))

        coVerify {
            subtaskDao.update(match {
                it.id == 5L && it.title == "Renombrada"
            })
        }
    }

    @Test
    fun `replaceSubtasksForTask does not touch a subtask that didn't change`() = runTest {
        val existing = Subtask(id = 5, taskId = 1L, title = "Sin cambios", completed = false, orderIndex = 0)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(existing)

        repository.replaceSubtasksForTask(1L, listOf(existing))

        coVerify(exactly = 0) { subtaskDao.update(any()) }
        coVerify(exactly = 0) { subtaskDao.insert(any()) }
        coVerify(exactly = 0) { subtaskDao.delete(any()) }
    }

    @Test
    fun `replaceSubtasksForTask physically deletes a subtask removed from the incoming list`() = runTest {
        val existing = Subtask(id = 5, taskId = 1L, title = "Se borra", orderIndex = 0)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(existing)
        coEvery { subtaskDao.delete(any()) } returns Unit

        repository.replaceSubtasksForTask(1L, emptyList())

        coVerify { subtaskDao.delete(match { it.id == 5L }) }
        coVerify(exactly = 0) { subtaskDao.update(any()) }
    }

    @Test
    fun `replaceSubtasksForTask updates a subtask when only its order changed`() = runTest {
        val first = Subtask(id = 1, taskId = 1L, title = "Uno", orderIndex = 0)
        val second = Subtask(id = 2, taskId = 1L, title = "Dos", orderIndex = 1)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(first, second)
        coEvery { subtaskDao.update(any()) } returns Unit

        // Reordered: "Dos" now comes first.
        repository.replaceSubtasksForTask(1L, listOf(second, first))

        coVerify { subtaskDao.update(match { it.id == 2L && it.orderIndex == 0 }) }
        coVerify { subtaskDao.update(match { it.id == 1L && it.orderIndex == 1 }) }
    }

    // A real "edit a task's subtask list" save mixes all three operations in one call — this is
    // the shape the UI actually produces, not just each operation in isolation like the tests
    // above (doc 06 punto 10 / Fase 7.2).
    @Test
    fun `replaceSubtasksForTask handles an insert, an update, an untouched row and a delete in the same call`() = runTest {
        val untouched = Subtask(id = 1, taskId = 1L, title = "Sin cambios", orderIndex = 0)
        val toRename = Subtask(id = 2, taskId = 1L, title = "Original", orderIndex = 1)
        val toDelete = Subtask(id = 3, taskId = 1L, title = "Se borra", orderIndex = 2)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(untouched, toRename, toDelete)
        coEvery { subtaskDao.update(any()) } returns Unit
        coEvery { subtaskDao.delete(any()) } returns Unit
        coEvery { subtaskDao.insert(any()) } returns 4L

        repository.replaceSubtasksForTask(
            1L,
            listOf(untouched, toRename.copy(title = "Renombrada"), Subtask(id = 0, taskId = 0, title = "Nueva"))
        )

        coVerify(exactly = 0) { subtaskDao.update(match { it.id == 1L }) }
        coVerify { subtaskDao.update(match { it.id == 2L && it.title == "Renombrada" && it.orderIndex == 1 }) }
        coVerify { subtaskDao.delete(match { it.id == 3L }) }
        coVerify { subtaskDao.insert(match { it.taskId == 1L && it.title == "Nueva" && it.orderIndex == 2 }) }
    }
}
