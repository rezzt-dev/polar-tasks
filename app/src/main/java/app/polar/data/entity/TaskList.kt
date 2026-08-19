package app.polar.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_lists")
data class TaskList(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val icon: String = "ic_list", // Default icon
  val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "orderIndex") val orderIndex: Int = 0,
  @ColumnInfo(name = "homeOrderIndex") val homeOrderIndex: Int = 0,
  @ColumnInfo(name = "isDependencyChain") val isDependencyChain: Boolean = false,
  @ColumnInfo(name = "color") val color: String = "#7F52FF"
)
