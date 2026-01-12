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
  
  override fun onBindViewHolder(holder: TaskListViewHolder, position: Int) {
    holder.bind(getItem(position))
  }
  
  inner class TaskListViewHolder(
    private val binding: ItemTaskListBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    
    fun bind(taskList: TaskList) {
      binding.tvListTitle.text = taskList.title
      
      // Set icon
      val iconResId = binding.root.context.resources.getIdentifier(
        taskList.icon,
        "drawable",
        binding.root.context.packageName
      )
      if (iconResId != 0) {
        binding.ivListIcon.setImageResource(iconResId)
      }
      
      binding.root.setOnClickListener {
        onItemClick(taskList)
      }
      
      binding.root.setOnLongClickListener {
        onItemLongClick(taskList)
      }
    }
  }
  
  private class TaskListDiffCallback : DiffUtil.ItemCallback<TaskList>() {
    override fun areItemsTheSame(oldItem: TaskList, newItem: TaskList): Boolean {
      return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: TaskList, newItem: TaskList): Boolean {
      return oldItem == newItem
    }
  }
}
