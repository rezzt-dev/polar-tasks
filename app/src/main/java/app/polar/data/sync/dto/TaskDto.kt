package app.polar.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shape for public.tasks exactly as specified in
// agent-docs/supabase-sync/05-contrato-interoperabilidad.md. `tags` travels as an array on the
// wire even though Room stores it as CSV — the CSV<->array conversion happens in TaskMapper,
// never here and never in the Task entity itself.
//
// `image_path` is the Storage bucket path (Task.imagePath locally), never the local content://
// imageUri — that stays 100% on-device (see doc 04, "qué NO se sincroniza").
@Serializable
data class TaskDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("list_id") val listId: String,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
    val tags: List<String> = emptyList(),
    @SerialName("due_date") val dueDate: Long? = null,
    @SerialName("order_index") val orderIndex: Int = 0,
    val recurrence: String = "NONE",
    val priority: Int = 0,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("time_estimate") val timeEstimate: Int = 0,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)
