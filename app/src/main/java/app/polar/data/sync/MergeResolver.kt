package app.polar.data.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// Pure decision functions for the client-side half of Last-Write-Wins (doc 04). The actual
// "who wins" decision always happens in the Postgres trigger (doc 03); these functions only
// interpret what the server already decided (push) or decide whether a remote row is newer than
// what's stored locally (pull). Kept pure/free of DAOs and the Supabase client so the merge
// semantics can be unit tested without a network layer.

enum class PushOutcome { WON, LOST }

// The trigger guarantees the returned row's updated_at is either exactly what we sent (we won)
// or newer (we lost and the server kept someone else's write) — never older.
fun resolvePushOutcome(localUpdatedAt: Long, returnedUpdatedAt: Long): PushOutcome =
    if (returnedUpdatedAt == localUpdatedAt) PushOutcome.WON else PushOutcome.LOST

enum class PullAction { INSERT, UPDATE, SKIP }

fun resolvePullAction(localUpdatedAt: Long?, remoteUpdatedAt: Long): PullAction = when {
    localUpdatedAt == null -> PullAction.INSERT
    remoteUpdatedAt > localUpdatedAt -> PullAction.UPDATE
    else -> PullAction.SKIP
}

// The pull cursor is saved one millisecond before pullStartedAt rather than pullStartedAt itself,
// so a remote row written with updated_at exactly equal to pullStartedAt (a same-millisecond
// collision) is still picked up by the *next* pull's `gt("updated_at", since)` filter instead of
// falling into the gap between "before the cursor" and "not strictly after it either"
// (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.7).
fun nextSyncCursor(pullStartedAt: Long): Long = pullStartedAt - 1

// A Realtime `postgres_changes` DELETE event only carries the row's replica identity in
// `oldRecord` (by default just the primary key, unless the table has REPLICA IDENTITY FULL
// configured — see agent-docs/analisis-implementacion-supabase-sync.md, Fase 6). That's too little
// to decode a full DTO, but it's enough to look the row up locally by uuid and, if it's already a
// confirmed tombstone, purge it the same way purgeTombstonesMissingRemote() does for a purge the
// client only discovers by polling.
fun extractUuidFromOldRecord(oldRecord: JsonObject): String? =
    oldRecord["id"]?.jsonPrimitive?.takeIf { it.isString }?.content
