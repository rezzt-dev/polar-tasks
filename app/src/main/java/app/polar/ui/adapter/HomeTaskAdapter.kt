package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Task
import app.polar.databinding.ItemCompletedHeaderBinding
import app.polar.databinding.ItemTaskBinding
import app.polar.databinding.ItemTaskHeaderBinding
import app.polar.ui.viewmodel.TaskViewModel

sealed class HomeItem {
    data class Header(val listId: Long, val title: String) : HomeItem() {
        override val id: Long = -listId - 1000 // Unique ID for headers
    }
    data class CompletedHeader(val listId: Long, val count: Int, val expanded: Boolean) : HomeItem() {
        override val id: Long = -listId - 2000 // Unique ID for completed headers
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
    private val onCompletedHeaderClick: (listId: Long) -> Unit = {},
    private val viewModel: TaskViewModel,
    private val lifecycleOwner: LifecycleOwner
) : ListAdapter<HomeItem, RecyclerView.ViewHolder>(HomeItemDiffCallback()) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_COMPLETED_HEADER = 1
        const val VIEW_TYPE_TASK = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HomeItem.Header -> VIEW_TYPE_HEADER
            is HomeItem.CompletedHeader -> VIEW_TYPE_COMPLETED_HEADER
            is HomeItem.TaskItem -> VIEW_TYPE_TASK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemTaskHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_COMPLETED_HEADER -> {
                val binding = ItemCompletedHeaderBinding.inflate(inflater, parent, false)
                CompletedHeaderViewHolder(binding, onCompletedHeaderClick)
            }
            VIEW_TYPE_TASK -> {
                val binding = ItemTaskBinding.inflate(inflater, parent, false)
                TaskViewHolder(binding, viewModel, lifecycleOwner)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HomeItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HomeItem.CompletedHeader -> (holder as CompletedHeaderViewHolder).bind(item)
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

    inner class HeaderViewHolder(
        private val binding: ItemTaskHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: HomeItem.Header) {
            binding.tvHeaderTitle.text = header.title
        }
    }

    inner class CompletedHeaderViewHolder(
        private val binding: ItemCompletedHeaderBinding,
        private val onClick: (listId: Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentExpanded = false

        init {
            // Ensure the arrow starts in the collapsed position
            binding.ivCompletedHeaderArrow.rotation = 0f
        }

        fun bind(header: HomeItem.CompletedHeader) {
            binding.tvCompletedHeaderTitle.text = itemView.context.getString(
                R.string.completed_tasks_header,
                header.count
            )
            binding.root.setOnClickListener { onClick(header.listId) }

            if (header.expanded != currentExpanded) {
                val targetRotation = if (header.expanded) 180f else 0f
                binding.ivCompletedHeaderArrow.animate()
                    .rotation(targetRotation)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                currentExpanded = header.expanded
            } else {
                binding.ivCompletedHeaderArrow.rotation = if (header.expanded) 180f else 0f
            }
        }
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val viewModel: TaskViewModel,
        private val lifecycleOwner: LifecycleOwner
    ) : RecyclerView.ViewHolder(binding.root) {

        private val subtaskAdapter = app.polar.ui.adapter.SubtaskAdapter(
            onCheckChanged = { subtask, _ -> viewModel.toggleSubtaskCompletion(subtask) },
            onDelete = { /* no delete from home screen */ }
        )

        private val typedValue = android.util.TypedValue()

        // Theme colors resolved once in init{} — theme does not change during adapter lifetime
        private val colorPrimary: Int
        private val colorOnSurface: Int
        private val colorPriorityHigh: Int
        private val colorPriorityMedium: Int
        private val colorPriorityLow: Int
        private val colorDateOverdue: Int

        private var subtaskObserver: androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>>? = null
        private var currentTaskId: Long? = null

        init {
            binding.recyclerSubtasks.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
            binding.recyclerSubtasks.adapter = subtaskAdapter
            binding.recyclerSubtasks.itemAnimator = null

            val ctx = itemView.context
            fun resolve(attr: Int): Int {
                ctx.theme.resolveAttribute(attr, typedValue, true)
                return typedValue.data
            }
            colorPrimary = resolve(android.R.attr.colorPrimary)
            colorOnSurface = resolve(com.google.android.material.R.attr.colorOnSurface)
            colorPriorityHigh = resolve(R.attr.colorPriorityHigh)
            colorPriorityMedium = resolve(R.attr.colorPriorityMedium)
            colorPriorityLow = resolve(R.attr.colorPriorityLow)
            colorDateOverdue = resolve(R.attr.colorDateOverdue)
        }

        fun bind(task: Task) {
            resetVisuals()
            currentTaskId = task.id
            itemView.tag = task.id
            binding.tvTaskTitle.text = task.title

            if (!task.description.isNullOrBlank()) {
                binding.tvTaskDescription.text = task.description
                binding.tvTaskDescription.visibility = View.VISIBLE
            } else {
                binding.tvTaskDescription.visibility = View.GONE
            }

            // Date — uses theme-resolved colors
            if (task.dueDate != null) {
                var dateStr = app.polar.util.DateUtils.formatTaskDate(itemView.context, task.dueDate)
                if (task.timeEstimate > 0) {
                    dateStr += " • ${app.polar.util.DateUtils.formatTimeEstimate(task.timeEstimate)}"
                }
                binding.tvTaskDate.text = dateStr
                binding.tvTaskDate.visibility = View.VISIBLE

                when {
                    !task.completed && app.polar.util.DateUtils.isOverdue(task.dueDate) ->
                        binding.tvTaskDate.setTextColor(colorDateOverdue)
                    app.polar.util.DateUtils.isToday(task.dueDate) ->
                        binding.tvTaskDate.setTextColor(colorPrimary)
                    else ->
                        binding.tvTaskDate.setTextColor(colorOnSurface)
                }
            } else if (task.timeEstimate > 0) {
                binding.tvTaskDate.text = app.polar.util.DateUtils.formatTimeEstimate(task.timeEstimate)
                binding.tvTaskDate.visibility = View.VISIBLE
                binding.tvTaskDate.setTextColor(colorOnSurface)
            } else {
                binding.tvTaskDate.visibility = View.GONE
            }

            if (!task.tags.isNullOrEmpty()) {
                binding.tvTaskTags.text = task.tags.split(",").joinToString(" ") { "#${it.trim()}" }
                binding.tvTaskTags.visibility = View.VISIBLE
            } else {
                binding.tvTaskTags.visibility = View.GONE
            }

            binding.tagsContainer.visibility = if (
                task.dueDate != null || task.timeEstimate > 0 || !task.tags.isNullOrEmpty()
            ) View.VISIBLE else View.GONE

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
                binding.viewPriorityStripe.visibility = View.VISIBLE
                binding.viewPriorityStripe.setBackgroundColor(priorityColor)
            } else {
                binding.viewPriorityStripe.visibility = View.GONE
            }

            updateVisuals(task.completed)

            binding.cbTaskComplete.setOnCheckedChangeListener { _, isChecked ->
                updateVisuals(isChecked)
                onTaskChecked(task, isChecked, itemView)
            }

            // Remove previous observer, attach new one
            unbind()

            val observer = androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> { subtasks ->
                if (subtasks.isNullOrEmpty()) {
                    binding.recyclerSubtasks.visibility = View.GONE
                } else {
                    binding.recyclerSubtasks.visibility = View.VISIBLE
                    subtaskAdapter.submitList(subtasks)
                }
            }
            viewModel.getSubtasksForTask(task.id).observe(lifecycleOwner, observer)
            subtaskObserver = observer

            // Las tareas completadas siguen siendo editables mediante clic, clic largo
            // o el botón de menú de tres puntos.
            binding.root.setOnClickListener { onTaskClick(task) }
            binding.root.setOnLongClickListener { onTaskLongClick(task, itemView) }
            binding.btnTaskOverflow.setOnClickListener { onTaskLongClick(task, itemView) }
        }

        fun unbind() {
            subtaskObserver?.let { observer ->
                currentTaskId?.let { id ->
                    viewModel.getSubtasksForTask(id).removeObserver(observer)
                }
            }
            subtaskObserver = null
        }

        private fun updateVisuals(isCompleted: Boolean) {
            if (isCompleted) {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvTaskTitle.alpha = 0.5f
            } else {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvTaskTitle.alpha = 1.0f
            }
        }

        fun resetVisuals() {
            // No itemView.animate().cancel() here — see TaskAdapter.TaskViewHolder.resetVisuals()
            // for why: this RecyclerView shares TaskItemAnimator with TaskAdapter, and cancelling
            // its in-flight ADD animation from inside bind() crashes with "Tmp detached view
            // should be removed from RecyclerView before it can be recycled". The property resets
            // below are enough to clear stale swipe state.
            itemView.alpha = 1.0f
            itemView.translationX = 0f
            itemView.translationY = 0f
            itemView.scaleX = 1.0f
            itemView.scaleY = 1.0f
        }
    }

    class HomeItemDiffCallback : DiffUtil.ItemCallback<HomeItem>() {
        override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean {
            // We intentionally include `completed` for TaskItems: the swipe animation leaves
            // a non-zero translationX on the ViewHolder. Without a full REMOVE+ADD, the view
            // stays visually displaced. Including `completed` forces DiffUtil to recreate the
            // ViewHolder on completion, clearing the swipe animation state naturally.
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
