package app.polar.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_lists")
data class TaskList(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val icon: String = "ic_list", // Default icon
  val createdAt: Long = System.currentTimeMillis()
)
