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
import app.polar.databinding.ItemCompletedHeaderBinding
import app.polar.databinding.ItemTaskBinding
import app.polar.ui.viewmodel.TaskViewModel
import com.google.android.material.R as MaterialR

class TaskAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: TaskViewModel,
    private val onCheckChanged: (Task, Boolean, android.view.View) -> Unit,
    private val onItemLongClick: (Task) -> Boolean,
    private val onItemClick: (Task) -> Unit = {},
    private val onCompletedHeaderClick: () -> Unit = {}
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(TaskListItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_COMPLETED_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TaskListItem.Header -> VIEW_TYPE_HEADER
            is TaskListItem.CompletedHeader -> VIEW_TYPE_COMPLETED_HEADER
            is TaskListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_COMPLETED_HEADER -> {
                val binding = ItemCompletedHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                CompletedHeaderViewHolder(binding, onCompletedHeaderClick)
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
            is TaskListItem.CompletedHeader -> (holder as CompletedHeaderViewHolder).bind(item.count, item.expanded)
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

    class CompletedHeaderViewHolder(
        private val binding: ItemCompletedHeaderBinding,
        private val onClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentExpanded = false

        init {
            binding.root.setOnClickListener { onClick() }
            // Ensure the arrow starts in the collapsed position
            binding.ivCompletedHeaderArrow.rotation = 0f
        }

        fun bind(count: Int, expanded: Boolean) {
            binding.tvCompletedHeaderTitle.text = binding.root.context.getString(
                R.string.completed_tasks_header,
                count
            )

            if (expanded != currentExpanded) {
                val targetRotation = if (expanded) 180f else 0f
                binding.ivCompletedHeaderArrow.animate()
                    .rotation(targetRotation)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                currentExpanded = expanded
            } else {
                binding.ivCompletedHeaderArrow.rotation = if (expanded) 180f else 0f
            }
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

        // Reusable TypedValue — avoids allocating a new object on every bind()
        private val typedValue = android.util.TypedValue()

        // Theme colors resolved once in init{} — theme does not change during adapter lifetime
        private val colorPrimary: Int
        private val colorOnSurface: Int
        private val colorSurfaceVariant: Int
        private val colorPriorityHigh: Int
        private val colorPriorityMedium: Int
        private val colorPriorityLow: Int
        private val colorDateOverdue: Int

        init {
            binding.recyclerSubtasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerSubtasks.adapter = subtaskAdapter
            binding.recyclerSubtasks.itemAnimator = null

            val ctx = binding.root.context
            fun resolve(attr: Int): Int {
                ctx.theme.resolveAttribute(attr, typedValue, true)
                return typedValue.data
            }
            colorPrimary = resolve(android.R.attr.colorPrimary)
            colorOnSurface = resolve(com.google.android.material.R.attr.colorOnSurface)
            colorSurfaceVariant = resolve(com.google.android.material.R.attr.colorSurfaceVariant)
            colorPriorityHigh = resolve(app.polar.R.attr.colorPriorityHigh)
            colorPriorityMedium = resolve(app.polar.R.attr.colorPriorityMedium)
            colorPriorityLow = resolve(app.polar.R.attr.colorPriorityLow)
            colorDateOverdue = resolve(app.polar.R.attr.colorDateOverdue)
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

                // Use pre-resolved theme colors (no allocations, no theme lookups)
                if (task.completed) {
                    binding.chainDot.setBackgroundResource(R.drawable.ic_circle)
                    val tint = android.content.res.ColorStateList.valueOf(colorPrimary)
                    binding.chainDot.backgroundTintList = tint
                    binding.chainLineTop.backgroundTintList = tint
                    binding.chainLineBottom.backgroundTintList = tint
                } else if (!isBlocked) {
                    binding.chainDot.setBackgroundResource(R.drawable.ic_circle_outline)
                    binding.chainDot.backgroundTintList = android.content.res.ColorStateList.valueOf(colorPrimary)
                    val surfaceTint = android.content.res.ColorStateList.valueOf(colorSurfaceVariant)
                    binding.chainLineTop.backgroundTintList = surfaceTint
                    binding.chainLineBottom.backgroundTintList = surfaceTint
                } else {
                    binding.chainDot.setBackgroundResource(R.drawable.ic_circle_outline)
                    val tint = android.content.res.ColorStateList.valueOf(colorSurfaceVariant)
                    binding.chainDot.backgroundTintList = tint
                    binding.chainLineTop.backgroundTintList = tint
                    binding.chainLineBottom.backgroundTintList = tint
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

            // Priority tinting — resolved from theme attributes, no hardcoded hex values
            val priorityColor = when (task.priority) {
                3 -> colorPriorityHigh
                2 -> colorPriorityMedium
                1 -> colorPriorityLow
                else -> colorOnSurface
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

            // Date logic — uses pre-resolved theme colors and pre-computed COLOR_DATE_OVERDUE
            if (task.dueDate != null) {
                binding.tvTaskDate.text = app.polar.util.DateUtils.formatTaskDate(itemView.context, task.dueDate)

                when {
                    !task.completed && app.polar.util.DateUtils.isOverdue(task.dueDate) ->
                        binding.tvTaskDate.setTextColor(colorDateOverdue)
                    app.polar.util.DateUtils.isToday(task.dueDate) ->
                        binding.tvTaskDate.setTextColor(colorPrimary)
                    else ->
                        binding.tvTaskDate.setTextColor(colorOnSurface)
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

            // Click listeners: las tareas completadas siguen siendo editables mediante
            // clic largo, clic en la fila o el botón de menú de tres puntos.
            if (!isBlocked) {
                binding.root.setOnLongClickListener { onItemLongClick(task) }
                binding.root.setOnClickListener { onItemClick(task) }
                binding.btnTaskOverflow.setOnClickListener { onItemLongClick(task) }
                binding.btnTaskOverflow.visibility = android.view.View.VISIBLE
            } else {
                binding.root.setOnLongClickListener(null)
                binding.root.setOnClickListener(null)
                binding.btnTaskOverflow.setOnClickListener(null)
                binding.btnTaskOverflow.visibility = android.view.View.GONE
            }
        }
        
        fun resetVisuals() {
             // No itemView.animate().cancel() here: the RecyclerView's own TaskItemAnimator
             // (DefaultItemAnimator) plays its ADD animation for a freshly inserted row using
             // that exact same ViewPropertyAnimator, and bind() calling resetVisuals() while
             // that animation is still running (e.g. right after creating a new task) cancels
             // it mid-flight. ValueAnimator.cancel() dispatches its end listener synchronously,
             // which re-enters the RecyclerView's own animation-finished bookkeeping for a view
             // holder that's still mid-bind and crashes with "Tmp detached view should be
             // removed from RecyclerView before it can be recycled". The property resets below
             // are enough to clear stale swipe state (alpha/translation/scale), which is all
             // this was ever meant to fix — none of it goes through ViewPropertyAnimator.
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
        if (itemFrom is TaskListItem.CompletedHeader || itemTo is TaskListItem.CompletedHeader) return false
        
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
        // We intentionally include `completed` here alongside the ID.
        // Reason: after a swipe gesture, the ItemTouchHelper leaves the view with a non-zero
        // translationX. If DiffUtil only does an in-place notifyItemChanged(), the same
        // ViewHolder is reused and the displaced translationX is never reset, making the item
        // appear "stuck" until the user navigates away. By treating a completed-state change
        // as a different item, DiffUtil does a REMOVE+ADD which destroys and recreates the
        // ViewHolder, naturally clearing all animation state.
        return when {
            oldItem is TaskListItem.Item && newItem is TaskListItem.Item -> {
                oldItem.task.id == newItem.task.id && oldItem.task.completed == newItem.task.completed
            }
            oldItem is TaskListItem.Header && newItem is TaskListItem.Header -> {
                oldItem.title == newItem.title
            }
            oldItem is TaskListItem.CompletedHeader && newItem is TaskListItem.CompletedHeader -> true
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
        return oldItem == newItem
    }
}
