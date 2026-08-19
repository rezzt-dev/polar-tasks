package app.polar.data.sync

import app.polar.data.entity.Reminder
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList

// Marks a row as freshly changed locally so the sync layer knows to push it
// (see agent-docs/supabase-sync/04-estrategia-sincronizacion.md, "Flujo de escritura local").
fun TaskList.touched(): TaskList = copy(updatedAt = System.currentTimeMillis(), dirty = true)

fun TaskList.touchedDeleted(): TaskList {
    val now = System.currentTimeMillis()
    return copy(deletedAt = now, updatedAt = now, dirty = true)
}

fun Task.touched(): Task = copy(updatedAt = System.currentTimeMillis(), dirty = true)

fun Task.touchedDeleted(): Task {
    val now = System.currentTimeMillis()
    return copy(isDeleted = true, deletedAt = now, updatedAt = now, dirty = true)
}

fun Task.touchedRestored(): Task =
    copy(isDeleted = false, deletedAt = null, updatedAt = System.currentTimeMillis(), dirty = true)

fun Subtask.touched(): Subtask = copy(updatedAt = System.currentTimeMillis(), dirty = true)

fun Subtask.touchedDeleted(): Subtask {
    val now = System.currentTimeMillis()
    return copy(deletedAt = now, updatedAt = now, dirty = true)
}

fun Reminder.touched(): Reminder = copy(updatedAt = System.currentTimeMillis(), dirty = true)

fun Reminder.touchedDeleted(): Reminder {
    val now = System.currentTimeMillis()
    return copy(isDeleted = true, deletedAt = now, updatedAt = now, dirty = true)
}

fun Reminder.touchedRestored(): Reminder =
    copy(isDeleted = false, deletedAt = null, updatedAt = System.currentTimeMillis(), dirty = true)
