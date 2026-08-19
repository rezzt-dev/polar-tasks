package app.polar.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
  tableName = "task_lists",
  indices = [Index(value = ["uuid"], unique = true)]
)
data class TaskList(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val icon: String = "ic_list", // Default icon
  val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(name = "orderIndex") val orderIndex: Int = 0,
  @ColumnInfo(name = "homeOrderIndex") val homeOrderIndex: Int = 0,
  @ColumnInfo(name = "isDependencyChain") val isDependencyChain: Boolean = false,
  @ColumnInfo(name = "color") val color: String = "#7F52FF",
  val uuid: String = java.util.UUID.randomUUID().toString(),
  val updatedAt: Long = System.currentTimeMillis(),
  val deletedAt: Long? = null,
  val dirty: Boolean = true
)
