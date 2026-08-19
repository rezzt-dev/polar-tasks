package app.polar.data.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeResolverTest {

    // --- resolvePushOutcome: interprets what the server trigger (doc 03) already decided ---

    @Test
    fun `push wins when server echoes back the same updatedAt we sent`() {
        assertEquals(PushOutcome.WON, resolvePushOutcome(localUpdatedAt = 1000L, returnedUpdatedAt = 1000L))
    }

    @Test
    fun `push loses when server returns a newer updatedAt than what we sent`() {
        assertEquals(PushOutcome.LOST, resolvePushOutcome(localUpdatedAt = 1000L, returnedUpdatedAt = 2000L))
    }

    // --- resolvePullAction: decides what to do with an incoming remote row ---

    @Test
    fun `pull inserts when the row doesn't exist locally yet`() {
        assertEquals(PullAction.INSERT, resolvePullAction(localUpdatedAt = null, remoteUpdatedAt = 1000L))
    }

    @Test
    fun `pull updates when remote is strictly newer than local`() {
        assertEquals(PullAction.UPDATE, resolvePullAction(localUpdatedAt = 1000L, remoteUpdatedAt = 2000L))
    }

    @Test
    fun `pull skips when local is newer than remote (local edit still pending push)`() {
        assertEquals(PullAction.SKIP, resolvePullAction(localUpdatedAt = 2000L, remoteUpdatedAt = 1000L))
    }

    @Test
    fun `pull skips when remote and local are already in sync`() {
        assertEquals(PullAction.SKIP, resolvePullAction(localUpdatedAt = 1000L, remoteUpdatedAt = 1000L))
    }

    // --- nextSyncCursor: avoids losing a row written at the exact millisecond the pull started ---

    @Test
    fun `next cursor is saved one millisecond before pullStartedAt`() {
        assertEquals(4999L, nextSyncCursor(pullStartedAt = 5000L))
    }

    @Test
    fun `a row updated exactly at pullStartedAt still passes the next pull's gt(updated_at, since) filter`() {
        val pullStartedAt = 5000L
        val nextCursor = nextSyncCursor(pullStartedAt)
        // pull() filters with gt("updated_at", since) — the row's updated_at must be strictly
        // greater than the saved cursor for the next pull to pick it up.
        assertEquals(true, pullStartedAt > nextCursor)
    }

    // --- extractUuidFromOldRecord: the only usable data in a Realtime hard-DELETE event ---

    @Test
    fun `extracts the uuid from a DELETE event's oldRecord`() {
        val oldRecord = buildJsonObject { put("id", "11111111-1111-1111-1111-111111111111") }
        assertEquals("11111111-1111-1111-1111-111111111111", extractUuidFromOldRecord(oldRecord))
    }

    @Test
    fun `returns null when oldRecord has no id field`() {
        val oldRecord: JsonObject = buildJsonObject { put("title", "orphaned column dump") }
        assertNull(extractUuidFromOldRecord(oldRecord))
    }

    @Test
    fun `returns null when id is not a JSON string`() {
        val oldRecord = buildJsonObject { put("id", JsonPrimitive(42)) }
        assertNull(extractUuidFromOldRecord(oldRecord))
    }
}
