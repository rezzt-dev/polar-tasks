package app.polar.data.model

import androidx.room.Embedded
import app.polar.data.entity.Task

data class TaskWithList(
    @Embedded val task: Task,
    val listTitle: String?,
    val totalSubtasks: Int,
    val completedSubtasks: Int
)
