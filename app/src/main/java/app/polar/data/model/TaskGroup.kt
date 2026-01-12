package app.polar.data.model

import app.polar.data.entity.Task

data class TaskGroup(
    val listId: Long,
    val title: String,
    val tasks: List<Task>
)
