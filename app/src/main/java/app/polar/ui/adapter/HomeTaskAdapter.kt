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
    data class Header(val listId: Long, val title: String, val progress: String = "") : HomeItem() {
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
    private val onTaskChecked: (Task, Boolean, android.view.View) -> Unit,
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
            holder.resetVisuals()
        }
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeaderTitle)
        
        fun bind(header: HomeItem.Header) {
            tvTitle.text = if (header.progress.isNotBlank()) "${header.title} ${header.progress}" else header.title
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
        private val viewPriorityStripe: View = itemView.findViewById(R.id.viewPriorityStripe)
        
        private val subtaskAdapter = app.polar.ui.adapter.SubtaskAdapter(
            onCheckChanged = { subtask, _ -> viewModel.toggleSubtaskCompletion(subtask) },
            onDelete = { /* no delete from home screen */ }
        )

        init {
            recyclerSubtasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
            recyclerSubtasks.adapter = subtaskAdapter
            recyclerSubtasks.itemAnimator = null
        }

        private var subtaskObserver: androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>>? = null
        private var currentTaskId: Long? = null

        fun bind(task: Task) {
            resetVisuals()
            currentTaskId = task.id
            itemView.tag = task.id
            tvTitle.text = task.title
            
            if (task.description.isNotBlank()) {
                tvDescription.text = task.description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }

            if (task.dueDate != null) {
                var dateStr = app.polar.util.DateUtils.formatTaskDate(itemView.context, task.dueDate)
                if (task.timeEstimate > 0) {
                    dateStr += " • ${task.timeEstimate} min"
                }
                tvDueDate.text = dateStr
                tvDueDate.visibility = View.VISIBLE
                
                when {
                    !task.completed && app.polar.util.DateUtils.isOverdue(task.dueDate) -> tvDueDate.setTextColor(android.graphics.Color.parseColor("#B3261E"))
                    app.polar.util.DateUtils.isToday(task.dueDate) -> {
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                        tvDueDate.setTextColor(typedValue.data)
                    }
                    else -> {
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                        tvDueDate.setTextColor(typedValue.data)
                    }
                }
            } else if (task.timeEstimate > 0) {
                tvDueDate.text = "${task.timeEstimate} min"
                tvDueDate.visibility = View.VISIBLE
                val typedValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                tvDueDate.setTextColor(typedValue.data)
            } else {
                tvDueDate.visibility = View.GONE
            }
            
            if (!task.tags.isNullOrEmpty()) {
                tvTags.text = task.tags.split(",").joinToString(" ") { "#${it.trim()}" }
                tvTags.visibility = View.VISIBLE
            } else {
                tvTags.visibility = View.GONE
            }

            tagsContainer.visibility = if (task.dueDate != null || task.timeEstimate > 0 || !task.tags.isNullOrEmpty()) View.VISIBLE else View.GONE

            cbCompleted.setOnCheckedChangeListener(null)
            cbCompleted.isChecked = task.completed

            // Priority tinting
            val priorityColor = when (task.priority) {
                3 -> android.graphics.Color.parseColor("#F44336") // High - Red
                2 -> android.graphics.Color.parseColor("#FF9800") // Medium - Orange
                1 -> android.graphics.Color.parseColor("#2196F3") // Low - Blue
                else -> {
                    val typedValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                    typedValue.data
                }
            }
            cbCompleted.buttonTintList = android.content.res.ColorStateList.valueOf(priorityColor)

            if (task.priority in 1..3) {
                viewPriorityStripe.visibility = View.VISIBLE
                viewPriorityStripe.setBackgroundColor(priorityColor)
            } else {
                viewPriorityStripe.visibility = View.GONE
            }

            updateVisuals(task.completed)

            cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                updateVisuals(isChecked)
                onTaskChecked(task, isChecked, itemView)
            }

            // Remove previous observer, attach new one
            unbind()
            
            val observer = androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> { subtasks ->
                if (subtasks.isNullOrEmpty()) {
                    recyclerSubtasks.visibility = View.GONE
                } else {
                    recyclerSubtasks.visibility = View.VISIBLE
                    subtaskAdapter.submitList(subtasks)
                }
            }
            viewModel.getSubtasksForTask(task.id).observe(lifecycleOwner, observer)
            subtaskObserver = observer

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

        fun resetVisuals() {
            itemView.animate().cancel()
            itemView.alpha = 1.0f
            itemView.translationX = 0f
            itemView.translationY = 0f
            itemView.scaleX = 1.0f
            itemView.scaleY = 1.0f
        }
    }

    class HomeItemDiffCallback : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            if (oldItem is HomeItem.TaskItem && newItem is HomeItem.TaskItem) {
                return oldItem.id == newItem.id && oldItem.task.completed == newItem.task.completed
            }
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            return oldItem == newItem
        }
    }
}
