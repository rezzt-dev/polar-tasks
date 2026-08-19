package app.polar.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shape for public.task_lists exactly as specified in
// agent-docs/supabase-sync/05-contrato-interoperabilidad.md — snake_case field names are the
// network contract; camelCase translation happens only here, never in the Room entity.
@Serializable
data class TaskListDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val icon: String = "ic_list",
    val color: String = "#7F52FF",
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("home_order_index") val homeOrderIndex: Int = 0,
    @SerialName("is_dependency_chain") val isDependencyChain: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)
