package app.polar.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
  ]
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
  @ColumnInfo(name = "orderIndex") val orderIndex: Int = 0
)
