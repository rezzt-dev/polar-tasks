package app.polar.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shape for public.reminders exactly as specified in
// agent-docs/supabase-sync/05-contrato-interoperabilidad.md. Intentionally has no relation to
// tasks/task_lists — reminders are a standalone entity both locally and on the wire.
@Serializable
data class ReminderDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String = "",
    @SerialName("date_time") val dateTime: Long,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long
)
