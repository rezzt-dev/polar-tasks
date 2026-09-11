package app.polar.data.model

data class ListTaskCount(
    val listId: Long,
    val title: String,
    val total: Int,
    val completed: Int
)
