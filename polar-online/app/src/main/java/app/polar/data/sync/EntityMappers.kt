package app.polar.data.sync

import app.polar.data.entity.Reminder
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.data.sync.dto.ReminderDto
import app.polar.data.sync.dto.SubtaskDto
import app.polar.data.sync.dto.TaskDto
import app.polar.data.sync.dto.TaskListDto

// Translates between Room entities (camelCase, local Long id + uuid) and the Supabase wire DTOs
// (snake_case, uuid-only) per agent-docs/supabase-sync/05-contrato-interoperabilidad.md. Foreign
// keys are resolved by the caller (SyncManager) since that requires a DAO lookup — these
// functions stay pure.

fun TaskList.toDto(userId: String): TaskListDto = TaskListDto(
    id = uuid,
    userId = userId,
    title = title,
    icon = icon,
    color = color,
    orderIndex = orderIndex,
    homeOrderIndex = homeOrderIndex,
    isDependencyChain = isDependencyChain,
    isDeleted = deletedAt != null,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TaskListDto.toEntity(localId: Long = 0): TaskList = TaskList(
    id = localId,
    title = title,
    icon = icon,
    createdAt = createdAt,
    orderIndex = orderIndex,
    homeOrderIndex = homeOrderIndex,
    isDependencyChain = isDependencyChain,
    color = color,
    uuid = id,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = false
)

// Room stores tags as "trabajo,urgente" CSV; the wire contract requires a real array (doc 05).
internal fun tagsCsvToList(csv: String): List<String> =
    csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

internal fun tagsListToCsv(tags: List<String>): String = tags.joinToString(",")

fun Task.toDto(userId: String, listUuid: String): TaskDto = TaskDto(
    id = uuid,
    userId = userId,
    listId = listUuid,
    title = title,
    description = description,
    completed = completed,
    tags = tagsCsvToList(tags),
    dueDate = dueDate,
    orderIndex = orderIndex,
    recurrence = recurrence,
    priority = priority,
    imagePath = imagePath,
    timeEstimate = timeEstimate,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// `localImageUri` must be threaded through from the row being replaced: it's the on-device
// content:// cache (never synced, see doc 04 "qué NO se sincroniza"), so a pull must not wipe it
// out just because every other field came from the server.
fun TaskDto.toEntity(localListId: Long, localId: Long = 0, localImageUri: String? = null): Task = Task(
    id = localId,
    listId = localListId,
    title = title,
    description = description,
    completed = completed,
    tags = tagsListToCsv(tags),
    createdAt = createdAt,
    dueDate = dueDate,
    orderIndex = orderIndex,
    recurrence = recurrence,
    isDeleted = isDeleted,
    priority = priority,
    imageUri = localImageUri,
    imagePath = imagePath,
    timeEstimate = timeEstimate,
    uuid = id,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = false
)

fun Subtask.toDto(userId: String, taskUuid: String): SubtaskDto = SubtaskDto(
    id = uuid,
    userId = userId,
    taskId = taskUuid,
    title = title,
    completed = completed,
    dueDate = dueDate,
    orderIndex = orderIndex,
    isDeleted = deletedAt != null,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SubtaskDto.toEntity(localTaskId: Long, localId: Long = 0): Subtask = Subtask(
    id = localId,
    taskId = localTaskId,
    title = title,
    completed = completed,
    dueDate = dueDate,
    orderIndex = orderIndex,
    createdAt = createdAt,
    uuid = id,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = false
)

fun Reminder.toDto(userId: String): ReminderDto = ReminderDto(
    id = uuid,
    userId = userId,
    title = title,
    description = description,
    dateTime = dateTime,
    isCompleted = isCompleted,
    latitude = latitude,
    longitude = longitude,
    radius = radius,
    locationName = locationName,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ReminderDto.toEntity(localId: Long = 0): Reminder = Reminder(
    id = localId,
    title = title,
    description = description,
    dateTime = dateTime,
    isCompleted = isCompleted,
    createdAt = createdAt,
    isDeleted = isDeleted,
    latitude = latitude,
    longitude = longitude,
    radius = radius,
    locationName = locationName,
    uuid = id,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = false
)
