package app.polar.data.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// SyncPrefs is exercised here with mocked Context/SharedPreferences instead of Robolectric (not a
// project dependency) — safe because SyncPrefs never touches any Android behavior beyond plain
// get/set/register calls, all mockable directly.
class SyncPrefsTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var syncPrefs: SyncPrefs

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor

        val context = mockk<Context>()
        every { context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE) } returns prefs

        syncPrefs = SyncPrefs(context)
    }

    @Test
    fun `lastSyncSuccessAt defaults to 0 when never set`() {
        every { prefs.getLong("last_sync_success_at", 0L) } returns 0L
        assertEquals(0L, syncPrefs.lastSyncSuccessAt)
    }

    @Test
    fun `lastSyncSuccessAt writes through to the editor`() {
        syncPrefs.lastSyncSuccessAt = 5000L
        verify { editor.putLong("last_sync_success_at", 5000L) }
    }

    @Test
    fun `lastSyncError defaults to null when never set`() {
        every { prefs.getString("last_sync_error", null) } returns null
        assertNull(syncPrefs.lastSyncError)
    }

    @Test
    fun `clearing lastSyncError after a successful sync writes null through`() {
        syncPrefs.lastSyncError = null
        verify { editor.putString("last_sync_error", null) }
    }

    @Test
    fun `lastSyncError records the failure message`() {
        syncPrefs.lastSyncError = "network unreachable"
        verify { editor.putString("last_sync_error", "network unreachable") }
    }

    @Test
    fun `lostConflictsCount defaults to 0 when never set`() {
        every { prefs.getInt("lost_conflicts_count", 0) } returns 0
        assertEquals(0, syncPrefs.lostConflictsCount)
    }

    @Test
    fun `lostConflictsCount writes through to the editor`() {
        syncPrefs.lostConflictsCount = 3
        verify { editor.putInt("lost_conflicts_count", 3) }
    }

    @Test
    fun `changes registers a SharedPreferences listener while collected and unregisters on cancel`() = runTest {
        val job = launch { syncPrefs.changes().collect {} }
        advanceUntilIdle()

        verify { prefs.registerOnSharedPreferenceChangeListener(any()) }

        job.cancel()
        advanceUntilIdle()

        verify { prefs.unregisterOnSharedPreferenceChangeListener(any()) }
    }
}
