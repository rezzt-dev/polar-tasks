package app.polar.ui.adapter

import app.polar.data.entity.Task

sealed class TaskListItem {
    data class Header(val title: String) : TaskListItem()
    data class Item(val task: Task) : TaskListItem()
}
