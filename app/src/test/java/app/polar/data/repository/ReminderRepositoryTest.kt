package app.polar.data.repository

import androidx.lifecycle.MutableLiveData
import app.polar.data.dao.ReminderDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// See agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 3.1: purging a trashed
// reminder used to be an unconditional physical delete, so it could vanish locally before its
// tombstone reached Supabase and reappear on the next pull. ReminderDao.emptyTrash()/
// permanentDelete() now only purge rows with dirty = 0; these tests cover how the repository
// surfaces that outcome to callers.
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
    fun `permanentDelete returns true when the dao actually purged the row`() = runTest {
        coEvery { reminderDao.permanentDelete(1L) } returns 1

        assertEquals(true, repository.permanentDelete(1L))
    }

    @Test
    fun `permanentDelete returns false when the dao refuses to purge an unsynced row`() = runTest {
        coEvery { reminderDao.permanentDelete(1L) } returns 0

        assertEquals(false, repository.permanentDelete(1L))
    }

    @Test
    fun `emptyTrash reports how many trashed reminders are still stuck after the purge attempt`() = runTest {
        coEvery { reminderDao.emptyTrash() } returns 2
        coEvery { reminderDao.getTrashCount() } returns 1

        val stillInTrash = repository.emptyTrash()

        assertEquals(1, stillInTrash)
        coVerify { reminderDao.emptyTrash() }
    }

    @Test
    fun `emptyTrash reports zero remaining when everything purged`() = runTest {
        coEvery { reminderDao.emptyTrash() } returns 2
        coEvery { reminderDao.getTrashCount() } returns 0

        assertEquals(0, repository.emptyTrash())
    }
}
