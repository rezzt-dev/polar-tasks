package app.polar.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "tasks",
  foreignKeys = [
    ForeignKey(
      entity = TaskList::class,
      parentColumns = ["id"],
      childColumns = ["listId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["listId"])]
)
data class Task(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val listId: Long,
  val title: String,
  val description: String = "",
  val completed: Boolean = false,
  val tags: String = "", // Comma-separated tags
  val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "dueDate") val dueDate: Long? = null,
  @ColumnInfo(name = "orderIndex") val orderIndex: Int = 0,
  @ColumnInfo(name = "recurrence") val recurrence: String = "NONE", // "NONE" o un valor de RecurrenceRule (DAILY, RRULE:...)
  @ColumnInfo(name = "isDeleted") val isDeleted: Boolean = false,
  @ColumnInfo(name = "priority") val priority: Int = 0, // 0: None, 1: Low, 2: Medium, 3: High
  @ColumnInfo(name = "imageUri") val imageUri: String? = null,
  @ColumnInfo(name = "timeEstimate") val timeEstimate: Int = 0,
  // Minutos antes de dueDate a los que suena el aviso (0, a la hora; TaskAlert.OFF, sin aviso)
  @ColumnInfo(name = "reminderOffset") val reminderOffset: Int = 0,
  // Aviso pospuesto desde la notificación: sustituye al aviso normal sin cambiar dueDate
  @ColumnInfo(name = "snoozedUntil") val snoozedUntil: Long? = null
)
