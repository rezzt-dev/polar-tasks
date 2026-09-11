package app.polar.ui.adapter

import app.polar.data.entity.Task

sealed class TaskListItem {
    data class Header(val title: String) : TaskListItem()
    data class CompletedHeader(val count: Int, val expanded: Boolean) : TaskListItem()
    data class Item(
        val task: Task,
        val isBlocked: Boolean = false,
        val isChainMode: Boolean = false,
        val isFirst: Boolean = false,
        val isLast: Boolean = false
    ) : TaskListItem()
}
