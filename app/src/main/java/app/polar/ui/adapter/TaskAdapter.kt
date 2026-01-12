package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Task
import app.polar.databinding.ItemTaskBinding
import app.polar.ui.viewmodel.TaskViewModel
import com.google.android.material.R as MaterialR

class TaskAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: TaskViewModel,
    private val onCheckChanged: (Task, Boolean) -> Unit,
    private val onItemLongClick: (Task) -> Boolean,
    private val onItemClick: (Task) -> Unit = {}
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(TaskListItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TaskListItem.Header -> VIEW_TYPE_HEADER
            is TaskListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_ITEM -> {
                val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                TaskViewHolder(binding, lifecycleOwner, viewModel, onCheckChanged, onItemLongClick, onItemClick)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TaskListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is TaskListItem.Item -> (holder as TaskViewHolder).bind(item.task)
        }
    }

    class HeaderViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView as TextView
        fun bind(title: String) {
            textView.text = title
        }
    }

    class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val lifecycleOwner: LifecycleOwner,
        private val viewModel: TaskViewModel,
        private val onCheckChanged: ((Task, Boolean) -> Unit),
        private val onItemLongClick: ((Task) -> Boolean),
        private val onItemClick: ((Task) -> Unit)
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.tvTaskTitle.text = task.title
            
            // Description logic
            if (task.description.isNullOrEmpty()) {
                binding.tvTaskDescription.visibility = android.view.View.GONE
            } else {
                binding.tvTaskDescription.visibility = android.view.View.VISIBLE
                binding.tvTaskDescription.text = task.description
            }

            // Tags logic
            if (task.tags.isNullOrEmpty()) {
                binding.tvTaskTags.visibility = android.view.View.GONE
            } else {
                binding.tvTaskTags.visibility = android.view.View.VISIBLE
                binding.tvTaskTags.text = task.tags
            }

            // Checkbox logic
            binding.cbTaskComplete.setOnCheckedChangeListener(null)
            binding.cbTaskComplete.isChecked = task.completed
            
            if (task.completed) {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvTaskTitle.alpha = 0.5f
            } else {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvTaskTitle.alpha = 1.0f
            }

            binding.cbTaskComplete.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(task, isChecked)
            }

            // Date logic
            if (task.dueDate != null) {
                binding.tvTaskDate.visibility = android.view.View.VISIBLE
                
                val format = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                val dateStr = when {
                    android.text.format.DateUtils.isToday(task.dueDate) -> binding.root.context.getString(R.string.today)
                    android.text.format.DateUtils.isToday(task.dueDate - 86400000L) -> binding.root.context.getString(R.string.tomorrow)
                    else -> format.format(java.util.Date(task.dueDate))
                }
                
                binding.tvTaskDate.text = dateStr
                
                // Color logic
                val now = System.currentTimeMillis()
                if (!task.completed && task.dueDate < now && !android.text.format.DateUtils.isToday(task.dueDate)) {
                     binding.tvTaskDate.setTextColor(android.graphics.Color.parseColor("#B3261E"))
                } else {
                     binding.tvTaskDate.setTextColor(android.graphics.Color.GRAY)
                }
                
            } else {
                binding.tvTaskDate.visibility = android.view.View.GONE
            }

            // Subtasks logic
            val adapter = app.polar.ui.adapter.SubtaskAdapter(
                 onCheckChanged = { subtask, _ -> viewModel.toggleSubtaskCompletion(subtask) },
                 onDelete = { subtask -> viewModel.deleteSubtask(subtask) }
            )
            binding.recyclerSubtasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerSubtasks.adapter = adapter
            
            viewModel.getSubtasksForTask(task.id).observe(lifecycleOwner) { subtasks ->
                if (subtasks.isNullOrEmpty()) {
                    binding.recyclerSubtasks.visibility = android.view.View.GONE
                } else {
                    binding.recyclerSubtasks.visibility = android.view.View.VISIBLE
                    adapter.submitList(subtasks)
                }
            }

            // Click listeners
            binding.root.setOnLongClickListener {
                 onItemLongClick(task)
            }
            binding.root.setOnClickListener {
                 onItemClick(task)
            }
        }
    }
}

class TaskListItemDiffCallback : DiffUtil.ItemCallback<TaskListItem>() {
    override fun areItemsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
        return if (oldItem is TaskListItem.Item && newItem is TaskListItem.Item) {
            oldItem.task.id == newItem.task.id
        } else if (oldItem is TaskListItem.Header && newItem is TaskListItem.Header) {
            oldItem.title == newItem.title
        } else {
            false
        }
    }

    override fun areContentsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
        return oldItem == newItem
    }
}
