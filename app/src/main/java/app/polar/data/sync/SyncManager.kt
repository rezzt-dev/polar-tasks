package app.polar.data.sync

import app.polar.data.dao.ReminderDao
import app.polar.data.dao.SubtaskDao
import app.polar.data.dao.TaskDao
import app.polar.data.dao.TaskListDao
import app.polar.data.entity.Reminder
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.sync.dto.ReminderDto
import app.polar.data.sync.dto.SubtaskDto
import app.polar.data.sync.dto.TaskDto
import app.polar.data.sync.dto.TaskListDto
import app.polar.util.AlarmManagerHelper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

// Orchestrates push (dirty local rows -> Supabase) then pull (remote changes since the last
// cursor -> Room), per agent-docs/supabase-sync/04-estrategia-sincronizacion.md. Conflict
// resolution is never decided here: the server trigger (doc 03) is the sole authority on which
// version of a row wins after a push — this class only compares the returned updated_at to know
// whether it won or lost, then either clears `dirty` or overwrites the local row with the winner.
//
// Also owns the optional Realtime layer (agent-docs/analisis-implementacion-supabase-sync.md,
// hallazgo 4.1 / Fase 6, Opción B): a `postgres_changes` subscription per table that applies each
// incoming event with exactly the same per-row logic as pull() (apply*Dto below), so two devices
// with the app open converge in near real time instead of waiting for the next 3/15-minute
// WorkManager tick. Realtime is a latency optimization only — push()/pull() via SyncWorker remain
// the source of truth reconciliation, so a missed or dropped Realtime event (network blip, app in
// background, subscribe failure) is always caught by the next regular sync. @Singleton so the
// channel/job started in startRealtime() survives across the ProcessLifecycleOwner onStart/onStop
// calls that drive it from PolarApplication, instead of a fresh unscoped instance losing that
// state on every call.
@Singleton
class SyncManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val taskListDao: TaskListDao,
    private val taskDao: TaskDao,
    private val subtaskDao: SubtaskDao,
    private val reminderDao: ReminderDao,
    private val syncPrefs: SyncPrefs,
    private val alarmHelper: AlarmManagerHelper
) {
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var realtimeChannel: RealtimeChannel? = null
    private var realtimeJob: Job? = null

    suspend fun sync(): Result<Unit> {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return Result.success(Unit)
        return try {
            val lostConflicts = push(userId)
            pull(userId)
            if (lostConflicts > 0) syncPrefs.lostConflictsCount += lostConflicts
            syncPrefs.lastSyncSuccessAt = System.currentTimeMillis()
            syncPrefs.lastSyncError = null
            Result.success(Unit)
        } catch (e: Exception) {
            // Surfaced in Settings as a persistent "sync is failing" state instead of the silent
            // Result.retry() loop this used to leave the user with
            // (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.9).
            syncPrefs.lastSyncError = e.message ?: e.javaClass.simpleName
            Result.failure(e)
        }
    }

    // Subscribes to `postgres_changes` on the 4 tables, filtered to this user, and applies each
    // event live with the same per-row logic pull() uses (doc 04, "Realtime"). Called from
    // PolarApplication's ProcessLifecycleOwner observer, mirrored with authentication state, so
    // the channel only exists while the app is in the foreground AND a session is active — no
    // background service, no battery cost while backgrounded. Idempotent: a second call while
    // already subscribed (e.g. two onStart ticks racing) is a no-op.
    suspend fun startRealtime() {
        if (realtimeChannel != null) return
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return

        val channel = supabaseClient.channel("polar-sync-$userId")
        realtimeChannel = channel

        // postgresChangeFlow() registers the table/filter config on the channel synchronously;
        // it must be called before subscribe() joins the realtime topic below.
        val taskListEvents = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "task_lists"
            filter("user_id", FilterOperator.EQ, userId)
        }
        val taskEvents = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tasks"
            filter("user_id", FilterOperator.EQ, userId)
        }
        val subtaskEvents = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "subtasks"
            filter("user_id", FilterOperator.EQ, userId)
        }
        val reminderEvents = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "reminders"
            filter("user_id", FilterOperator.EQ, userId)
        }

        // Each table's flow is wrapped in its own catch so a decode failure on one event (e.g. an
        // unexpected payload shape) can't silently kill the subscription for the other three
        // tables — it's logged and the merged collector keeps running.
        realtimeJob = realtimeScope.launch {
            merge(
                taskListEvents.onEach { handleTaskListEvent(it) }.catch { logRealtimeError("task_lists", it) },
                taskEvents.onEach { handleTaskEvent(it) }.catch { logRealtimeError("tasks", it) },
                subtaskEvents.onEach { handleSubtaskEvent(it) }.catch { logRealtimeError("subtasks", it) },
                reminderEvents.onEach { handleReminderEvent(it) }.catch { logRealtimeError("reminders", it) }
            ).collect {}
        }

        try {
            channel.subscribe()
        } catch (e: Exception) {
            // Subscribe failed (offline, server unreachable) — leave the channel registered so
            // the SDK's own reconnect logic can still bring it up later; the periodic/foreground
            // SyncWorker pull remains the safety net regardless.
            logRealtimeError("subscribe", e)
        }
    }

    // Tears down the channel and its collector job. Called on backgrounding (ProcessLifecycleOwner
    // onStop) and on sign-out/session loss, so a signed-out or backgrounded app never holds an open
    // websocket for nothing. Uses Realtime.removeChannel() rather than channel.unsubscribe()
    // directly: unsubscribe() alone only sends the leave message, it doesn't drop the channel from
    // the plugin's subscriptions map or disconnect the underlying websocket — removeChannel() does
    // both (and disconnects the socket outright once it was the last subscription), which is what
    // actually frees the connection while backgrounded instead of leaving it idling.
    suspend fun stopRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        val channel = realtimeChannel ?: return
        realtimeChannel = null
        try {
            supabaseClient.realtime.removeChannel(channel)
        } catch (e: Exception) {
            // Already disconnected (e.g. socket dropped before we got here) — nothing left to do.
            logRealtimeError("removeChannel", e)
        }
    }

    private fun logRealtimeError(context: String, e: Throwable) {
        android.util.Log.e("SyncManager", "Realtime error ($context)", e)
    }

    private suspend fun handleTaskListEvent(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> applyTaskListDto(action.decodeRecord())
            is PostgresAction.Update -> applyTaskListDto(action.decodeRecord())
            is PostgresAction.Delete -> {
                // No confirmed-tombstone concept for task lists today (hallazgo 4.5 scoped this
                // to tasks/reminders only, the only two tables with a Trash UI) — nothing to do.
            }
            else -> {}
        }
    }

    private suspend fun handleTaskEvent(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> applyTaskDto(action.decodeRecord())
            is PostgresAction.Update -> applyTaskDto(action.decodeRecord())
            is PostgresAction.Delete -> {
                // A hard DELETE only ever happens once pg_cron physically purges an already
                // 30-day-old tombstone (doc 03) — permanentDelete()'s own `dirty = 0` guard
                // (hallazgo 3.1) makes this safe even if the local row somehow isn't a confirmed
                // tombstone yet, it simply no-ops.
                extractUuidFromOldRecord(action.oldRecord)
                    ?.let { uuid -> taskDao.getByUuid(uuid) }
                    ?.let { local -> taskDao.permanentDelete(local.id) }
            }
            else -> {}
        }
    }

    private suspend fun handleSubtaskEvent(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> applySubtaskDto(action.decodeRecord())
            is PostgresAction.Update -> applySubtaskDto(action.decodeRecord())
            is PostgresAction.Delete -> {
                // Same scope decision as task lists above: subtasks have no standalone confirmed-
                // tombstone/Trash concept, they only disappear via their parent task's purge.
            }
            else -> {}
        }
    }

    private suspend fun handleReminderEvent(action: PostgresAction) {
        when (action) {
            is PostgresAction.Insert -> applyReminderDto(action.decodeRecord())
            is PostgresAction.Update -> applyReminderDto(action.decodeRecord())
            is PostgresAction.Delete -> {
                extractUuidFromOldRecord(action.oldRecord)
                    ?.let { uuid -> reminderDao.getByUuid(uuid) }
                    ?.let { local -> reminderDao.permanentDelete(local.id) }
            }
            else -> {}
        }
    }

    // Whether this device has any task list, task, subtask, or reminder at all (including
    // soft-deleted rows) — used to decide whether the first-login merge/discard prompt (doc 04,
    // "Primera sincronización") needs to ask anything, since an empty device has nothing to
    // merge or discard.
    suspend fun hasLocalData(): Boolean {
        return taskListDao.getAllTaskListsIncludingDeletedSnapshot().isNotEmpty() ||
            taskDao.getAllTasksSnapshot().isNotEmpty() ||
            subtaskDao.getAllSubtasksSnapshot().isNotEmpty() ||
            reminderDao.getAllRemindersSnapshot().isNotEmpty()
    }

    // First-login "discard local, use only the cloud" choice (doc 04, "Primera sincronización" /
    // agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.2). Wipes every local table
    // and resets the cursor so the following pull() fetches the account's full remote history,
    // instead of merging local test/duplicate data into it. Destructive locally by design; the UI
    // must confirm first.
    suspend fun discardLocalAndPullFromCloud(): Result<Unit> {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            taskListDao.deleteAll()
            taskDao.deleteAll()
            subtaskDao.deleteAll()
            reminderDao.deleteAll()
            syncPrefs.lastSyncAt = 0L
            pull(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Symmetric counterpart to pushAllOverwrite() (hallazgo 4.6): same "wipe local + full pull"
    // operation already used by the first-login "discard local, use only the cloud" choice above,
    // exposed under the name Settings' destructive "download everything, overwrite this device"
    // button uses to mirror pushAllOverwrite()'s naming.
    suspend fun pullAllOverwrite(): Result<Unit> = discardLocalAndPullFromCloud()

    // "Upload full copy, overwrite everything" (Settings -> caution dialog). Unlike sync(), this
    // treats the local device as the sole source of truth: every local row is force-touched so it
    // wins the server's LWW trigger (doc 03) regardless of dirty state, and any remote row this
    // device has no record of at all — never pulled, never created here — is soft-deleted so the
    // cloud ends up matching this device exactly. Destructive by design; the UI must confirm first.
    suspend fun pushAllOverwrite(): Result<Unit> {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            touchAllLocalRows()
            push(userId)
            deleteRemoteOrphans(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "pushAllOverwrite failed", e)
            Result.failure(e)
        }
    }

    private suspend fun touchAllLocalRows() {
        taskListDao.updateAll(taskListDao.getAllTaskListsIncludingDeletedSnapshot().map { it.touched() })
        taskDao.updateAll(taskDao.getAllTasksSnapshot().map { it.touched() })
        subtaskDao.getAllSubtasksSnapshot().forEach { subtaskDao.update(it.touched()) }
        reminderDao.getAllRemindersSnapshot().forEach { reminderDao.update(it.touched()) }
    }

    private suspend fun deleteRemoteOrphans(userId: String) {
        val now = System.currentTimeMillis()
        val localListUuids = taskListDao.getAllTaskListsIncludingDeletedSnapshot().map { it.uuid }.toSet()
        deleteOrphans("task_lists", userId, localListUuids, now) { it.decodeList<TaskListDto>().map { dto -> dto.id } }

        val localTaskUuids = taskDao.getAllTasksSnapshot().map { it.uuid }.toSet()
        deleteOrphans("tasks", userId, localTaskUuids, now) { it.decodeList<TaskDto>().map { dto -> dto.id } }

        val localSubtaskUuids = subtaskDao.getAllSubtasksSnapshot().map { it.uuid }.toSet()
        deleteOrphans("subtasks", userId, localSubtaskUuids, now) { it.decodeList<SubtaskDto>().map { dto -> dto.id } }

        val localReminderUuids = reminderDao.getAllRemindersSnapshot().map { it.uuid }.toSet()
        deleteOrphans("reminders", userId, localReminderUuids, now) { it.decodeList<ReminderDto>().map { dto -> dto.id } }
    }

    // Fetches every remote row not already tombstoned for `table`, decodes the ids with
    // [decodeIds] (typed per-table since each DTO is a distinct class), and soft-deletes whichever
    // ones aren't in [localUuids] — remote data this device has no local record of whatsoever.
    private suspend fun deleteOrphans(
        table: String,
        userId: String,
        localUuids: Set<String>,
        now: Long,
        decodeIds: (io.github.jan.supabase.postgrest.result.PostgrestResult) -> List<String>
    ) {
        val remoteResult = supabaseClient.from(table).select {
            filter {
                eq("user_id", userId)
                eq("is_deleted", false)
            }
        }
        val remoteIds = decodeIds(remoteResult)
        remoteIds.filterNot { it in localUuids }.forEach { orphanId ->
            supabaseClient.from(table).update(RemoteTombstone(deletedAt = now, updatedAt = now)) {
                filter { eq("id", orphanId) }
            }
        }
    }

    @Serializable
    private data class RemoteTombstone(
        @SerialName("is_deleted") val isDeleted: Boolean = true,
        @SerialName("deleted_at") val deletedAt: Long,
        @SerialName("updated_at") val updatedAt: Long
    )

    // Dependency order matters: a task pushed before its list, or a subtask before its task,
    // would resolve a dangling parent uuid. Reminders have no relations so their position doesn't matter.
    // Returns how many rows lost their LWW conflict across all four tables, so sync() can surface
    // it to the user instead of discarding those edits in total silence
    // (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.3).
    private suspend fun push(userId: String): Int {
        var lostConflicts = 0
        lostConflicts += pushTaskLists(userId)
        lostConflicts += pushTasks(userId)
        lostConflicts += pushSubtasks(userId)
        lostConflicts += pushReminders(userId)
        return lostConflicts
    }

    // Every dirty row of a table is now sent in a single upsert(list) request instead of one
    // upsert per row (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.8) — a
    // reorder of 50 tasks used to fire 50 sequential HTTP requests, now fires exactly one.
    // Winners are matched back to their local row by uuid via a map keyed on the returned DTO's
    // id, never by list position/order, which Postgrest does not guarantee is preserved across
    // a batched upsert.
    private suspend fun pushTaskLists(userId: String): Int {
        val dirty = taskListDao.getDirtyTaskLists()
        if (dirty.isEmpty()) return 0
        val winners = supabaseClient.from("task_lists")
            .upsert(dirty.map { it.toDto(userId) }) { select() }
            .decodeList<TaskListDto>()
            .associateBy { it.id }
        var lostConflicts = 0
        val won = mutableListOf<TaskList>()
        val lost = mutableListOf<TaskList>()
        dirty.forEach { local ->
            val winner = winners[local.uuid] ?: return@forEach
            when (resolvePushOutcome(local.updatedAt, winner.updatedAt)) {
                PushOutcome.WON -> won += local.copy(dirty = false)
                PushOutcome.LOST -> {
                    lost += winner.toEntity(localId = local.id)
                    lostConflicts++
                }
            }
        }
        if (won.isNotEmpty()) taskListDao.updateAll(won)
        if (lost.isNotEmpty()) taskListDao.updateAll(lost)
        return lostConflicts
    }

    private suspend fun pushTasks(userId: String): Int {
        // listId is a Room FK (CASCADE) so this lookup should never miss in practice; the filter
        // is only a defensive guard against pushing a dto with no resolvable parent uuid.
        val dirty = taskDao.getDirtyTasks().filter { taskListDao.getListById(it.listId) != null }
        if (dirty.isEmpty()) return 0
        val winners = supabaseClient.from("tasks")
            .upsert(dirty.map { local -> local.toDto(userId, taskListDao.getListById(local.listId)!!.uuid) }) { select() }
            .decodeList<TaskDto>()
            .associateBy { it.id }
        var lostConflicts = 0
        val won = mutableListOf<Task>()
        val lost = mutableListOf<Task>()
        dirty.forEach { local ->
            val winner = winners[local.uuid] ?: return@forEach
            when (resolvePushOutcome(local.updatedAt, winner.updatedAt)) {
                PushOutcome.WON -> won += local.copy(dirty = false)
                PushOutcome.LOST -> {
                    val localListId = taskListDao.getByUuid(winner.listId)?.id ?: local.listId
                    lost += winner.toEntity(localListId = localListId, localId = local.id, localImageUri = local.imageUri)
                    lostConflicts++
                }
            }
        }
        if (won.isNotEmpty()) taskDao.updateAll(won)
        if (lost.isNotEmpty()) taskDao.updateAll(lost)
        return lostConflicts
    }

    private suspend fun pushSubtasks(userId: String): Int {
        val dirty = subtaskDao.getDirtySubtasks().filter { taskDao.getTaskById(it.taskId) != null }
        if (dirty.isEmpty()) return 0
        val winners = supabaseClient.from("subtasks")
            .upsert(dirty.map { local -> local.toDto(userId, taskDao.getTaskById(local.taskId)!!.uuid) }) { select() }
            .decodeList<SubtaskDto>()
            .associateBy { it.id }
        var lostConflicts = 0
        val won = mutableListOf<Subtask>()
        val lost = mutableListOf<Subtask>()
        dirty.forEach { local ->
            val winner = winners[local.uuid] ?: return@forEach
            when (resolvePushOutcome(local.updatedAt, winner.updatedAt)) {
                PushOutcome.WON -> won += local.copy(dirty = false)
                PushOutcome.LOST -> {
                    val localTaskId = taskDao.getByUuid(winner.taskId)?.id ?: local.taskId
                    lost += winner.toEntity(localTaskId = localTaskId, localId = local.id)
                    lostConflicts++
                }
            }
        }
        if (won.isNotEmpty()) subtaskDao.updateAll(won)
        if (lost.isNotEmpty()) subtaskDao.updateAll(lost)
        return lostConflicts
    }

    private suspend fun pushReminders(userId: String): Int {
        val dirty = reminderDao.getDirtyReminders()
        if (dirty.isEmpty()) return 0
        val winners = supabaseClient.from("reminders")
            .upsert(dirty.map { it.toDto(userId) }) { select() }
            .decodeList<ReminderDto>()
            .associateBy { it.id }
        var lostConflicts = 0
        val won = mutableListOf<Reminder>()
        val lost = mutableListOf<Reminder>()
        dirty.forEach { local ->
            val winner = winners[local.uuid] ?: return@forEach
            when (resolvePushOutcome(local.updatedAt, winner.updatedAt)) {
                PushOutcome.WON -> won += local.copy(dirty = false)
                PushOutcome.LOST -> {
                    lost += winner.toEntity(localId = local.id)
                    lostConflicts++
                }
            }
        }
        if (won.isNotEmpty()) reminderDao.updateAll(won)
        if (lost.isNotEmpty()) reminderDao.updateAll(lost)
        return lostConflicts
    }

    private suspend fun pull(userId: String) {
        val since = syncPrefs.lastSyncAt
        // Captured before any query runs, not after: a row written on the server mid-pull must
        // still be picked up by the *next* sync rather than being skipped by a cursor that raced
        // past it.
        val pullStartedAt = System.currentTimeMillis()
        // The 4 tables' fetches are independent GETs — firing them concurrently instead of one
        // after another turns ~4 sequential network round trips into ~1 (bounded by the slowest
        // of the four), which is most of where the "opening the app takes 15-20s to catch up"
        // latency was coming from. Applying the results still happens sequentially afterwards, in
        // the same dependency order as before (lists -> tasks -> subtasks -> reminders), since
        // applyTaskDto/applySubtaskDto need their parent row already in Room.
        coroutineScope {
            val remoteTaskLists = async { fetchTaskLists(userId, since) }
            val remoteTasks = async { fetchTasks(userId, since) }
            val remoteSubtasks = async { fetchSubtasks(userId, since) }
            val remoteReminders = async { fetchReminders(userId, since) }

            remoteTaskLists.await().forEach { applyTaskListDto(it) }
            remoteTasks.await().forEach { applyTaskDto(it) }
            remoteSubtasks.await().forEach { applySubtaskDto(it) }
            remoteReminders.await().forEach { applyReminderDto(it) }
        }
        purgeTombstonesMissingRemote(userId)
        syncPrefs.lastSyncAt = nextSyncCursor(pullStartedAt)
    }

    // A tombstone that already made it to Supabase (dirty = 0) can later be purged for real by
    // the server (pg_cron, 30 days, see doc 03) — a physical DELETE that a normal incremental
    // pull can never observe, since a row that stops existing never shows up in any future
    // `gt(updated_at, since)` result. Left alone, the local trash keeps a "ghost" row forever
    // (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 4.5). Only tasks and
    // reminders have a Trash UI backed by this dirty=0 gate, so only those two tables are checked
    // here; scoped to the (typically small) set of already-confirmed local tombstones rather than
    // the whole table.
    private suspend fun purgeTombstonesMissingRemote(userId: String) {
        val confirmedTasks = taskDao.getConfirmedTrashedTasksSnapshot()
        val confirmedReminders = reminderDao.getConfirmedTrashedRemindersSnapshot()
        if (confirmedTasks.isEmpty() && confirmedReminders.isEmpty()) return

        coroutineScope {
            val remoteTaskIds = async {
                if (confirmedTasks.isEmpty()) emptySet() else supabaseClient.from("tasks").select {
                    filter {
                        eq("user_id", userId)
                        isIn("id", confirmedTasks.map { it.uuid })
                    }
                }.decodeList<TaskDto>().map { it.id }.toSet()
            }
            val remoteReminderIds = async {
                if (confirmedReminders.isEmpty()) emptySet() else supabaseClient.from("reminders").select {
                    filter {
                        eq("user_id", userId)
                        isIn("id", confirmedReminders.map { it.uuid })
                    }
                }.decodeList<ReminderDto>().map { it.id }.toSet()
            }

            confirmedTasks.filter { it.uuid !in remoteTaskIds.await() }.forEach { taskDao.permanentDelete(it.id) }
            confirmedReminders.filter { it.uuid !in remoteReminderIds.await() }.forEach { reminderDao.permanentDelete(it.id) }
        }
    }

    private suspend fun fetchTaskLists(userId: String, since: Long): List<TaskListDto> =
        supabaseClient.from("task_lists").select {
            filter {
                eq("user_id", userId)
                gt("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()

    private suspend fun fetchTasks(userId: String, since: Long): List<TaskDto> =
        supabaseClient.from("tasks").select {
            filter {
                eq("user_id", userId)
                gt("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()

    private suspend fun fetchSubtasks(userId: String, since: Long): List<SubtaskDto> =
        supabaseClient.from("subtasks").select {
            filter {
                eq("user_id", userId)
                gt("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()

    private suspend fun fetchReminders(userId: String, since: Long): List<ReminderDto> =
        supabaseClient.from("reminders").select {
            filter {
                eq("user_id", userId)
                gt("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
        }.decodeList()

    // Applies a single remote row with the client-side half of LWW (resolvePullAction) and
    // reschedules its alarm if needed. Shared by pull() (looping over an incremental batch) and
    // the Realtime handlers above (one event at a time) — doc 04's Realtime section explicitly
    // requires "aplicando cada evento entrante con la misma lógica de merge del pull", so this is
    // the single implementation both paths call instead of two copies that could drift apart.
    private suspend fun applyTaskListDto(dto: TaskListDto) {
        val local = taskListDao.getByUuid(dto.id)
        when (resolvePullAction(local?.updatedAt, dto.updatedAt)) {
            PullAction.INSERT -> taskListDao.insert(dto.toEntity())
            PullAction.UPDATE -> taskListDao.update(dto.toEntity(localId = local!!.id))
            PullAction.SKIP -> {}
        }
    }

    private suspend fun applyTaskDto(dto: TaskDto) {
        // Parent list not synced locally yet (out-of-order arrival); it'll be resolved on a
        // later pull/event once task_lists catches up.
        val localListId = taskListDao.getByUuid(dto.listId)?.id ?: return
        val local = taskDao.getByUuid(dto.id)
        val localId = when (resolvePullAction(local?.updatedAt, dto.updatedAt)) {
            PullAction.INSERT -> taskDao.insert(dto.toEntity(localListId = localListId))
            PullAction.UPDATE -> {
                taskDao.update(dto.toEntity(localListId = localListId, localId = local!!.id, localImageUri = local.imageUri))
                local.id
            }
            PullAction.SKIP -> null
        } ?: return
        // A task created/edited from the other app/device only notifies on this device once
        // its alarm is (re)scheduled here (doc 06, punto 9).
        if (dto.isDeleted || dto.completed || dto.dueDate == null) {
            alarmHelper.cancelTaskAlarm(localId)
        } else {
            alarmHelper.scheduleTaskAlarm(localId, dto.dueDate)
        }
    }

    private suspend fun applySubtaskDto(dto: SubtaskDto) {
        val localTaskId = taskDao.getByUuid(dto.taskId)?.id ?: return
        val local = subtaskDao.getByUuid(dto.id)
        val localId = when (resolvePullAction(local?.updatedAt, dto.updatedAt)) {
            PullAction.INSERT -> subtaskDao.insert(dto.toEntity(localTaskId = localTaskId))
            PullAction.UPDATE -> {
                subtaskDao.update(dto.toEntity(localTaskId = localTaskId, localId = local!!.id))
                local.id
            }
            PullAction.SKIP -> null
        } ?: return
        if (dto.isDeleted || dto.completed || dto.dueDate == null) {
            alarmHelper.cancelSubtaskAlarm(localId)
        } else {
            alarmHelper.scheduleSubtaskAlarm(localId, dto.dueDate)
        }
    }

    private suspend fun applyReminderDto(dto: ReminderDto) {
        val local = reminderDao.getByUuid(dto.id)
        val localId = when (resolvePullAction(local?.updatedAt, dto.updatedAt)) {
            PullAction.INSERT -> reminderDao.insert(dto.toEntity())
            PullAction.UPDATE -> {
                reminderDao.update(dto.toEntity(localId = local!!.id))
                local.id
            }
            PullAction.SKIP -> null
        } ?: return
        if (dto.isDeleted || dto.isCompleted) {
            alarmHelper.cancelReminderAlarm(localId)
        } else {
            alarmHelper.scheduleReminderAlarm(localId, dto.dateTime)
        }
    }
}
