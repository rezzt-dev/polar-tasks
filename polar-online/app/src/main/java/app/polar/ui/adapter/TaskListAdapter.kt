package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.data.entity.TaskList
import app.polar.databinding.ItemTaskListBinding

class TaskListAdapter(
  private val onItemClick: (TaskList) -> Unit,
  private val onItemLongClick: (TaskList) -> Boolean
) : ListAdapter<TaskList, TaskListAdapter.TaskListViewHolder>(TaskListDiffCallback()) {
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskListViewHolder {
    val binding = ItemTaskListBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return TaskListViewHolder(binding)
  }
  
  private var selectedListId: Long? = null

  override fun onBindViewHolder(holder: TaskListViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  /** Marca como activa en el cajón la lista abierta (o ninguna con `null`). */
  fun setSelectedListId(listId: Long?) {
    if (selectedListId == listId) return
    val previous = currentList.indexOfFirst { it.id == selectedListId }
    selectedListId = listId
    val current = currentList.indexOfFirst { it.id == listId }
    if (previous >= 0) notifyItemChanged(previous)
    if (current >= 0) notifyItemChanged(current)
  }
  
  inner class TaskListViewHolder(
    private val binding: ItemTaskListBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    
    fun bind(taskList: TaskList) {
      binding.tvListTitle.text = taskList.title
      binding.root.isActivated = taskList.id == selectedListId
      
      // Set icon
      val iconResId = binding.root.context.resources.getIdentifier(
        taskList.icon,
        "drawable",
        binding.root.context.packageName
      )
      if (iconResId != 0) {
        binding.ivListIcon.setImageResource(iconResId)
      }
      
      try {
        val parsedColor = android.graphics.Color.parseColor(taskList.color)
        // Un color elegido en un tema oscuro (p. ej. blanco) puede desaparecer en uno claro:
        // si apenas contrasta con la superficie se usa el tinte del tema.
        val surface = com.google.android.material.color.MaterialColors.getColor(
          binding.root, com.google.android.material.R.attr.colorSurface
        )
        if (androidx.core.graphics.ColorUtils.calculateContrast(
            androidx.core.graphics.ColorUtils.setAlphaComponent(parsedColor, 255), surface
          ) < MIN_ICON_CONTRAST
        ) {
          binding.ivListIcon.clearColorFilter()
        } else {
          binding.ivListIcon.setColorFilter(parsedColor)
        }
      } catch (e: Exception) {
        // color no válido: volver al tinte del tema en lugar de heredar el de otra fila reciclada
        binding.ivListIcon.clearColorFilter()
      }
      
      binding.root.setOnClickListener {
        onItemClick(taskList)
      }
      
      binding.root.setOnLongClickListener {
        onItemLongClick(taskList)
      }
    }
  }
  
  companion object {
    private const val MIN_ICON_CONTRAST = 1.8
  }

  private class TaskListDiffCallback : DiffUtil.ItemCallback<TaskList>() {
    override fun areItemsTheSame(oldItem: TaskList, newItem: TaskList): Boolean {
      return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: TaskList, newItem: TaskList): Boolean {
      return oldItem == newItem
    }
  }

  fun onItemMove(fromPosition: Int, toPosition: Int) {
      val currentList = currentList.toMutableList()
      if (fromPosition < toPosition) {
          for (i in fromPosition until toPosition) {
              java.util.Collections.swap(currentList, i, i + 1)
          }
      } else {
          for (i in fromPosition downTo toPosition + 1) {
              java.util.Collections.swap(currentList, i, i - 1)
          }
      }
      submitList(currentList)
  }
}
