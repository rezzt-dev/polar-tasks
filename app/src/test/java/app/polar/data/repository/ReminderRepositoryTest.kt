package app.polar.data.repository

import androidx.lifecycle.MutableLiveData
import app.polar.data.dao.ReminderDao
import app.polar.data.entity.Reminder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Offline trash semantics: emptyTrash()/permanentDelete() are unconditional physical deletes
// (no dirty-row guard, see agent-docs/eliminacion-supabase/). These tests cover that the
// repository is a thin delegation layer over the dao for trash operations.
class ReminderRepositoryTest {

    private val reminderDao = mockk<ReminderDao>()

    init {
        // ReminderRepository wires these LiveData/Flow properties eagerly in its constructor.
        every { reminderDao.getAllReminders() } returns MutableLiveData()
        every { reminderDao.getActiveReminders() } returns MutableLiveData()
        every { reminderDao.getAllRemindersFlow() } returns flowOf(emptyList())
        every { reminderDao.getActiveRemindersFlow() } returns flowOf(emptyList())
    }

    private val repository = ReminderRepository(reminderDao)

    @Test
    fun `softDelete marks the reminder as deleted via dao update`() = runTest {
        val reminder = Reminder(id = 1, title = "Delete me", dateTime = 1000L)
        coEvery { reminderDao.update(any()) } returns Unit

        repository.softDelete(reminder)

        coVerify { reminderDao.update(match { it.id == 1L && it.isDeleted }) }
    }

    @Test
    fun `restore marks the reminder as not deleted via dao update`() = runTest {
        val reminder = Reminder(id = 1, title = "Restore me", dateTime = 1000L, isDeleted = true)
        coEvery { reminderDao.update(any()) } returns Unit

        repository.restore(reminder)

        coVerify { reminderDao.update(match { it.id == 1L && !it.isDeleted }) }
    }

    @Test
    fun `permanentDelete delegates to dao physical delete`() = runTest {
        coEvery { reminderDao.permanentDelete(1L) } returns Unit

        repository.permanentDelete(1L)

        coVerify { reminderDao.permanentDelete(1L) }
    }

    @Test
    fun `emptyTrash delegates to dao physical purge of every deleted row`() = runTest {
        coEvery { reminderDao.emptyTrash() } returns Unit

        repository.emptyTrash()

        coVerify { reminderDao.emptyTrash() }
    }
}
