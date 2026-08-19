package app.polar.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
  tableName = "subtasks",
  foreignKeys = [
    ForeignKey(
      entity = Task::class,
      parentColumns = ["id"],
      childColumns = ["taskId"],
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class Subtask(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val taskId: Long,
  val title: String,
  val completed: Boolean = false,
  @ColumnInfo(name = "dueDate") val dueDate: Long? = null,
  val orderIndex: Int = 0,
  val createdAt: Long = System.currentTimeMillis()
)
