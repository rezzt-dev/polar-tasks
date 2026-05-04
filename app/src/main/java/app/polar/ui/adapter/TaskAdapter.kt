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
    private val onCheckChanged: (Task, Boolean, android.view.View) -> Unit,
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
            is TaskListItem.Item -> (holder as TaskViewHolder).bind(
                task = item.task,
                isBlocked = item.isBlocked,
                isChainMode = item.isChainMode,
                isFirst = item.isFirst,
                isLast = item.isLast
            )
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TaskViewHolder) {
            holder.resetVisuals()
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
        private val onCheckChanged: ((Task, Boolean, android.view.View) -> Unit),
        private val onItemLongClick: ((Task) -> Boolean),
        private val onItemClick: ((Task) -> Unit)
    ) : RecyclerView.ViewHolder(binding.root) {

        private val subtaskAdapter = app.polar.ui.adapter.SubtaskAdapter(
            onCheckChanged = { subtask, _ -> viewModel.toggleSubtaskCompletion(subtask) },
            onDelete = { subtask -> viewModel.deleteSubtask(subtask) }
        )
        private var currentSubtaskLiveData: androidx.lifecycle.LiveData<List<app.polar.data.entity.Subtask>>? = null

        init {
            binding.recyclerSubtasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerSubtasks.adapter = subtaskAdapter
            binding.recyclerSubtasks.itemAnimator = null
        }

        fun bind(task: Task, isBlocked: Boolean = false, isChainMode: Boolean = false, isFirst: Boolean = false, isLast: Boolean = false) {
            // Reset any stale visual state from previous swipe/animation
            resetVisuals()
            itemView.tag = task.id
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
                binding.tvTaskTags.text = task.tags.split(",").joinToString(" ") { "#${it.trim()}" }
            }

            // --- Chain Mode Visuals ---
            if (isChainMode) {
                binding.chainTimelineColumn.visibility = android.view.View.VISIBLE
                // Hide top line for the first item
                binding.chainLineTop.visibility = if (isFirst) android.view.View.INVISIBLE else android.view.View.VISIBLE
                // Hide bottom line if it's the last item
                binding.chainLineBottom.visibility = if (isLast) android.view.View.INVISIBLE else android.view.View.VISIBLE

                val ctx = itemView.context
                val typedValue = android.util.TypedValue()
                if (task.completed) {
                     // Completed dot = filled and primary color
                    binding.chainDot.setBackgroundResource(R.drawable.ic_circle)
                    ctx.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                    binding.chainDot.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                    binding.chainLineTop.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                    binding.chainLineBottom.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                } else if (!isBlocked) {
                    // Active (unlocked, not completed) = outline and primary color
                    binding.chainDot.setBackgroundResource(R.drawable.ic_circle_outline)
                    ctx.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                    binding.chainDot.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                    ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
                    binding.chainLineTop.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                    binding.chainLineBottom.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                } else {
                    // Future/blocked dot = outline and surface variant
                    binding.chainDot.setBackgroundResource(R.drawable.ic_circle_outline)
                    ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
                    binding.chainDot.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                    binding.chainLineTop.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                    binding.chainLineBottom.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
                }
            } else {
                binding.chainTimelineColumn.visibility = android.view.View.GONE
            }

            // --- Blocked state ---
            if (isBlocked) {
                itemView.alpha = 0.45f
                binding.cbTaskComplete.isEnabled = false
                binding.chainLockedBadge.visibility = android.view.View.VISIBLE
                binding.root.isClickable = false
            } else {
                itemView.alpha = 1.0f
                binding.cbTaskComplete.isEnabled = true
                binding.chainLockedBadge.visibility = android.view.View.GONE
                binding.root.isClickable = true
            }

            // Checkbox logic
            binding.cbTaskComplete.setOnCheckedChangeListener(null)
            binding.cbTaskComplete.isChecked = task.completed

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
            binding.cbTaskComplete.buttonTintList = android.content.res.ColorStateList.valueOf(priorityColor)
            
            if (task.priority in 1..3) {
                binding.viewPriorityStripe.visibility = android.view.View.VISIBLE
                binding.viewPriorityStripe.setBackgroundColor(priorityColor)
            } else {
                binding.viewPriorityStripe.visibility = android.view.View.GONE
            }
            
            if (task.completed) {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvTaskTitle.alpha = 0.5f
            } else {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvTaskTitle.alpha = if (isBlocked) 0.5f else 1.0f
            }

            if (!isBlocked) {
                binding.cbTaskComplete.setOnCheckedChangeListener { _, isChecked ->
                    onCheckChanged(task, isChecked, itemView)
                }
            }

            // Date logic
            if (task.dueDate != null) {
                binding.tvTaskDate.text = app.polar.util.DateUtils.formatTaskDate(itemView.context, task.dueDate)
                
                when {
                    !task.completed && app.polar.util.DateUtils.isOverdue(task.dueDate) -> {
                        binding.tvTaskDate.setTextColor(android.graphics.Color.parseColor("#B3261E"))
                    }
                    app.polar.util.DateUtils.isToday(task.dueDate) -> {
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                        binding.tvTaskDate.setTextColor(typedValue.data)
                    }
                    else -> {
                        val typedValue = android.util.TypedValue()
                        itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                        binding.tvTaskDate.setTextColor(typedValue.data)
                    }
                }
                
                binding.tvTaskDate.visibility = android.view.View.VISIBLE
            } else {
                binding.tvTaskDate.visibility = android.view.View.GONE
            }

            if (task.dueDate != null || !task.tags.isNullOrEmpty()) {
                binding.tagsContainer.visibility = android.view.View.VISIBLE
            } else {
                binding.tagsContainer.visibility = android.view.View.GONE
            }

            // Subtasks
            currentSubtaskLiveData?.removeObservers(lifecycleOwner)
            val liveData = viewModel.getSubtasksForTask(task.id)
            currentSubtaskLiveData = liveData
            liveData.observe(lifecycleOwner) { subtasks ->
                if (subtasks.isNullOrEmpty()) {
                    binding.recyclerSubtasks.visibility = android.view.View.GONE
                } else {
                    binding.recyclerSubtasks.visibility = android.view.View.VISIBLE
                    subtaskAdapter.submitList(subtasks)
                }
            }

            // Click listeners
            if (!isBlocked) {
                binding.root.setOnLongClickListener { onItemLongClick(task) }
                binding.root.setOnClickListener { onItemClick(task) }
            } else {
                binding.root.setOnLongClickListener(null)
                binding.root.setOnClickListener(null)
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
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        // Prevent moving headers or moving items above headers if desirable
        // For simplicity, allow swap if both are items
        val list = currentList.toMutableList()
        val itemFrom = list[fromPosition]
        val itemTo = list[toPosition]
        
        // Block dragging headers or dropping onto headers
        if (itemFrom is TaskListItem.Header || itemTo is TaskListItem.Header) return false
        
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(list, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(list, i, i - 1)
            }
        }
        submitList(list)
        return true
    }
}

class TaskListItemDiffCallback : DiffUtil.ItemCallback<TaskListItem>() {
    override fun areItemsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
        return if (oldItem is TaskListItem.Item && newItem is TaskListItem.Item) {
            oldItem.task.id == newItem.task.id && oldItem.task.completed == newItem.task.completed
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
