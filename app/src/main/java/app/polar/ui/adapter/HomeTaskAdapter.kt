package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HomeItem {
    data class Header(val listId: Long, val title: String) : HomeItem() {
        override val id: Long = -listId - 1000 // Unique ID for headers
    }
    data class TaskItem(val task: Task) : HomeItem() {
        override val id: Long = task.id
    }
    
    abstract val id: Long
}

class HomeTaskAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskLongClick: (Task, View) -> Boolean,
    private val onTaskChecked: (Task, Boolean) -> Unit,
    private val viewModel: app.polar.ui.viewmodel.TaskViewModel,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(HomeItemDiffCallback()) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_TASK = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HomeItem.Header -> VIEW_TYPE_HEADER
            is HomeItem.TaskItem -> VIEW_TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_task_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_TASK -> {
                val view = inflater.inflate(R.layout.item_task, parent, false)
                TaskViewHolder(view, viewModel, lifecycleOwner)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HomeItem.TaskItem -> (holder as TaskViewHolder).bind(item.task)
        }
    }
    
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TaskViewHolder) {
            holder.unbind()
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeaderTitle)
        
        fun bind(header: HomeItem.Header) {
            tvTitle.text = header.title
        }
    }

    inner class TaskViewHolder(
        itemView: View,
        private val viewModel: app.polar.ui.viewmodel.TaskViewModel,
        private val lifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        private val tvDueDate: TextView = itemView.findViewById(R.id.tvTaskDate)
        private val tvTags: TextView = itemView.findViewById(R.id.tvTaskTags)
        private val cbCompleted: CheckBox = itemView.findViewById(R.id.cbTaskComplete)
        private val tagsContainer: View = itemView.findViewById(R.id.tagsContainer)
        private val recyclerSubtasks: RecyclerView = itemView.findViewById(R.id.recyclerSubtasks)
        
        private var subtaskObserver: androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>>? = null
        private var currentTaskId: Long? = null

        fun bind(task: Task) {
            currentTaskId = task.id
            tvTitle.text = task.title
            
            if (task.description.isNotBlank()) {
                tvDescription.text = task.description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }

            if (task.dueDate != null) {
                val format = SimpleDateFormat("MMM dd", Locale.getDefault())
                val dateStr = format.format(Date(task.dueDate))
                val displayDate = if (android.text.format.DateUtils.isToday(task.dueDate)) "hoy" else dateStr
                
                tvDueDate.text = displayDate
                
                val isOverdue = !task.completed && task.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(task.dueDate)
                if (isOverdue) {
                    tvDueDate.setTextColor(android.graphics.Color.RED)
                } else {
                    val typedValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
                    tvDueDate.setTextColor(typedValue.data)
                }
                tvDueDate.visibility = View.VISIBLE
            } else {
                tvDueDate.visibility = View.GONE
            }
            
             if (!task.tags.isNullOrEmpty()) {
                tvTags.text = task.tags.split(",").joinToString(" ") { "#${it.trim()}" }
                tagsContainer.visibility = View.VISIBLE
            } else {
                tagsContainer.visibility = View.GONE
                if (task.dueDate == null) {
                     // Hide container if both are gone
                }
            }

            cbCompleted.setOnCheckedChangeListener(null)
            cbCompleted.isChecked = task.completed
            updateVisuals(task.completed)

            cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                updateVisuals(isChecked)
                onTaskChecked(task, isChecked)
            }

            // --- Subtasks Logic ---
            // Remove previous observer if any
            unbind()
            
            val observer = androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> { subtasks ->
                 if (subtasks.isNullOrEmpty()) {
                     recyclerSubtasks.visibility = View.GONE
                 } else {
                     recyclerSubtasks.visibility = View.VISIBLE
                     // Reuse SubtaskAdapter but we need a minimal version or standard one?
                     // Standard one has checkboxes. We want them to complete the subtask.
                     // IMPORTANT: Disable nested scrolling for this inner recycler
                     recyclerSubtasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
                     val adapter = app.polar.ui.adapter.SubtaskAdapter(
                         onCheckChanged = { subtask, isChecked ->
                             viewModel.toggleSubtaskCompletion(subtask)
                         },
                         onDelete = { 
                             // No delete from home screen mini-view, simpler
                         }
                     )
                     recyclerSubtasks.adapter = adapter
                     adapter.submitList(subtasks)
                 }
            }
            viewModel.getSubtasksForTask(task.id).observe(lifecycleOwner, observer)
            subtaskObserver = observer
            // --- End Subtasks Logic ---

            itemView.setOnClickListener { onTaskClick(task) }
            itemView.setOnLongClickListener { onTaskLongClick(task, itemView) }
        }
        
        fun unbind() {
             subtaskObserver?.let {
                 currentTaskId?.let { id ->
                     viewModel.getSubtasksForTask(id).removeObserver(it)
                 }
             }
             subtaskObserver = null
        }

        private fun updateVisuals(isCompleted: Boolean) {
            if (isCompleted) {
                tvTitle.paintFlags = tvTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                tvTitle.alpha = 0.5f
            } else {
                tvTitle.paintFlags = tvTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvTitle.alpha = 1.0f
            }
        }
    }

    class HomeItemDiffCallback : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            return oldItem == newItem
        }
    }
}
