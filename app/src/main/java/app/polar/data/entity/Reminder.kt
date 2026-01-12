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
    val createdAt: Long = System.currentTimeMillis()
)
