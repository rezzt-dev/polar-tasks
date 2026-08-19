package app.polar.data.repository

import androidx.lifecycle.MutableLiveData
import app.polar.data.dao.TaskDao
import app.polar.data.entity.Subtask
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
    
    @Test
    fun `softDeleteTask marks task deleted and dirty via dao update`() = runTest {
        val task = Task(id = 1, listId = 1L, title = "Delete me")
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns emptyList()

        repository.softDeleteTask(task)

        coVerify {
            taskDao.update(match {
                it.id == 1L && it.isDeleted && it.deletedAt != null && it.dirty
            })
        }
    }

    // Regression test for hallazgo 3.2 (agent-docs/analisis-implementacion-supabase-sync.md):
    // trashing a task used to leave its subtasks "active", so a later purge of the task could
    // cascade-delete (via Room's ON DELETE CASCADE) subtasks whose tombstone never reached
    // Supabase, resurrecting them on the next pull.
    @Test
    fun `softDeleteTask cascades the tombstone to every active subtask`() = runTest {
        val task = Task(id = 1, listId = 1L, title = "Delete me")
        val subtask = Subtask(id = 5, taskId = 1L, title = "Child", orderIndex = 0, dirty = false)
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(subtask)
        coEvery { subtaskDao.update(any()) } returns Unit

        repository.softDeleteTask(task)

        coVerify {
            subtaskDao.update(match {
                it.id == 5L && it.deletedAt != null && it.dirty
            })
        }
    }

    // --- Trash purge: dirty-aware, see hallazgo 3.1 ---

    @Test
    fun `permanentDeleteTask returns true when the dao actually purged the row`() = runTest {
        coEvery { taskDao.permanentDelete(1L) } returns 1

        assertEquals(true, repository.permanentDeleteTask(1L))
    }

    // The dao query only deletes rows with dirty = 0 (and no dirty subtasks); returning 0 means
    // it refused because the tombstone hadn't reached Supabase yet, so the row must stay put.
    @Test
    fun `permanentDeleteTask returns false when the dao refuses to purge an unsynced row`() = runTest {
        coEvery { taskDao.permanentDelete(1L) } returns 0

        assertEquals(false, repository.permanentDeleteTask(1L))
    }

    @Test
    fun `emptyTrash reports how many trashed tasks are still stuck after the purge attempt`() = runTest {
        coEvery { taskDao.emptyTrash() } returns 3
        coEvery { taskDao.getTrashCount() } returns 2

        val stillInTrash = repository.emptyTrash()

        assertEquals(2, stillInTrash)
        coVerify { taskDao.emptyTrash() }
    }

    // --- replaceSubtasksForTask: diff-based replace, see doc 06 punto 3 ---

    @Test
    fun `replaceSubtasksForTask inserts a brand new subtask (id 0) as dirty`() = runTest {
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns emptyList()
        coEvery { subtaskDao.insert(any()) } returns 10L

        repository.replaceSubtasksForTask(1L, listOf(Subtask(id = 0, taskId = 0, title = "Nueva")))

        coVerify {
            subtaskDao.insert(match {
                it.taskId == 1L && it.title == "Nueva" && it.orderIndex == 0 && it.dirty
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
                it.id == 5L && it.title == "Renombrada" && it.dirty
            })
        }
    }

    @Test
    fun `replaceSubtasksForTask does not touch a subtask that didn't change`() = runTest {
        val existing = Subtask(id = 5, taskId = 1L, title = "Sin cambios", completed = false, orderIndex = 0, dirty = false)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(existing)

        repository.replaceSubtasksForTask(1L, listOf(existing))

        coVerify(exactly = 0) { subtaskDao.update(any()) }
        coVerify(exactly = 0) { subtaskDao.insert(any()) }
    }

    @Test
    fun `replaceSubtasksForTask soft-deletes a subtask that was removed from the incoming list`() = runTest {
        val existing = Subtask(id = 5, taskId = 1L, title = "Se borra", orderIndex = 0)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(existing)
        coEvery { subtaskDao.update(any()) } returns Unit

        repository.replaceSubtasksForTask(1L, emptyList())

        coVerify {
            subtaskDao.update(match {
                it.id == 5L && it.deletedAt != null && it.dirty
            })
        }
    }

    @Test
    fun `replaceSubtasksForTask marks a subtask dirty when only its order changed`() = runTest {
        val first = Subtask(id = 1, taskId = 1L, title = "Uno", orderIndex = 0, dirty = false)
        val second = Subtask(id = 2, taskId = 1L, title = "Dos", orderIndex = 1, dirty = false)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(first, second)
        coEvery { subtaskDao.update(any()) } returns Unit

        // Reordered: "Dos" now comes first.
        repository.replaceSubtasksForTask(1L, listOf(second, first))

        coVerify { subtaskDao.update(match { it.id == 2L && it.orderIndex == 0 && it.dirty }) }
        coVerify { subtaskDao.update(match { it.id == 1L && it.orderIndex == 1 && it.dirty }) }
    }

    // A real "edit a task's subtask list" save mixes all three operations in one call — this is
    // the shape the UI actually produces, not just each operation in isolation like the tests
    // above (doc 06 punto 10 / agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 6 /
    // Fase 7.4).
    @Test
    fun `replaceSubtasksForTask handles an insert, an update, an untouched row and a delete in the same call`() = runTest {
        val untouched = Subtask(id = 1, taskId = 1L, title = "Sin cambios", orderIndex = 0, dirty = false)
        val toRename = Subtask(id = 2, taskId = 1L, title = "Original", orderIndex = 1, dirty = false)
        val toDelete = Subtask(id = 3, taskId = 1L, title = "Se borra", orderIndex = 2, dirty = false)
        coEvery { subtaskDao.getSubtasksForTaskDirect(1L) } returns listOf(untouched, toRename, toDelete)
        coEvery { subtaskDao.update(any()) } returns Unit
        coEvery { subtaskDao.insert(any()) } returns 4L

        repository.replaceSubtasksForTask(
            1L,
            listOf(untouched, toRename.copy(title = "Renombrada"), Subtask(id = 0, taskId = 0, title = "Nueva"))
        )

        coVerify(exactly = 0) { subtaskDao.update(match { it.id == 1L }) }
        coVerify { subtaskDao.update(match { it.id == 2L && it.title == "Renombrada" && it.orderIndex == 1 && it.dirty }) }
        coVerify { subtaskDao.update(match { it.id == 3L && it.deletedAt != null && it.dirty }) }
        coVerify { subtaskDao.insert(match { it.taskId == 1L && it.title == "Nueva" && it.orderIndex == 2 && it.dirty }) }
    }
}
