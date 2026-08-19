package app.polar.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shape for public.subtasks exactly as specified in
// agent-docs/supabase-sync/05-contrato-interoperabilidad.md.
@Serializable
data class SubtaskDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("task_id") val taskId: String,
    val title: String,
    val completed: Boolean = false,
    @SerialName("due_date") val dueDate: Long? = null,
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)
