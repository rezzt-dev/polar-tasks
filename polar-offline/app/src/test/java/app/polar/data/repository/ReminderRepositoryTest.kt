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

// Trash operations for reminders are purely local: moving to the trash and restoring flip
// isDeleted by id, and purging deletes the rows right away.
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

    private val reminder = Reminder(id = 1L, title = "Recuerdame", dateTime = 1000L)

    @Test
    fun `softDelete moves the reminder to the trash by id`() = runTest {
        coEvery { reminderDao.softDelete(1L) } returns Unit

        repository.softDelete(reminder)

        coVerify { reminderDao.softDelete(1L) }
    }

    @Test
    fun `restore brings the reminder back from the trash by id`() = runTest {
        coEvery { reminderDao.restore(1L) } returns Unit

        repository.restore(reminder.copy(isDeleted = true))

        coVerify { reminderDao.restore(1L) }
    }

    @Test
    fun `permanentDelete deletes the row`() = runTest {
        coEvery { reminderDao.permanentDelete(1L) } returns Unit

        repository.permanentDelete(1L)

        coVerify { reminderDao.permanentDelete(1L) }
    }

    @Test
    fun `emptyTrash deletes every trashed reminder`() = runTest {
        coEvery { reminderDao.emptyTrash() } returns Unit

        repository.emptyTrash()

        coVerify { reminderDao.emptyTrash() }
    }
}
