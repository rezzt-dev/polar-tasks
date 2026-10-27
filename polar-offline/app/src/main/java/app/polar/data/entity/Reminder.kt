package app.polar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dateTime: Long, // Epoch millis
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float? = null,
    val locationName: String? = null,
    // Además de a su hora, avisa al entrar en el radio de las coordenadas (ver GeofenceHelper)
    val notifyOnArrival: Boolean = false
)
