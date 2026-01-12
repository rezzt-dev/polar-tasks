package app.polar.ui.adapter

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.data.entity.Task
import app.polar.data.model.TaskGroup
import app.polar.databinding.ItemTaskGroupBinding
import app.polar.R
import app.polar.ui.activity.TaskDetailActivity
import app.polar.util.DragDropHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeTaskAdapter(
    private val onGroupMove: (Int, Int) -> Unit,
    private val onTaskClick: ((Task) -> Unit)? = null,
    private val onTaskLongClick: ((Task, View) -> Boolean)? = null
) : ListAdapter<TaskGroup, HomeTaskAdapter.GroupViewHolder>(GroupDiffCallback()), DragDropHelper.ItemTouchHelperAdapter {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemTaskGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding, onTaskLongClick)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
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
        onGroupMove(fromPosition, toPosition)
        return true
    }

    fun getCurrentGroups(): List<TaskGroup> {
        return currentList
    }

    class GroupViewHolder(
        private val binding: ItemTaskGroupBinding,
        private val onTaskLongClick: ((Task, View) -> Boolean)?
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(group: TaskGroup) {
            binding.tvGroupTitle.text = group.title
            binding.llTasksContainer.removeAllViews()

            val context = binding.root.context
            
            group.tasks.forEach { task ->
                val taskView = LayoutInflater.from(context).inflate(R.layout.item_task_minimal, binding.llTasksContainer, false)
                
                // ... (view finding code same as before) ...
                val tvTitle = taskView.findViewById<TextView>(R.id.tvTaskTitle)
                val tvDescription = taskView.findViewById<TextView>(R.id.tvTaskDescription)
                val tvDueDate = taskView.findViewById<TextView>(R.id.tvDueDate)
                val tvTags = taskView.findViewById<TextView>(R.id.tvTags)
                val cbCompleted = taskView.findViewById<CheckBox>(R.id.cbCompleted)
                
                tvTitle.text = task.title
                updateTaskVisuals(tvTitle, task.completed)

                if (task.description.isNotBlank()) {
                    tvDescription.text = task.description
                    tvDescription.visibility = View.VISIBLE
                } else {
                    tvDescription.visibility = View.GONE
                }

                if (task.dueDate != null) {
                    val format = SimpleDateFormat("MMM dd", Locale.getDefault())
                    val dateStr = format.format(Date(task.dueDate))
                    val isOverdue = !task.completed && task.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(task.dueDate)
                    tvDueDate.text = if (android.text.format.DateUtils.isToday(task.dueDate)) "Today" else dateStr
                    if (isOverdue) {
                        tvDueDate.setTextColor(android.graphics.Color.RED)
                        tvDueDate.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFEBEE"))
                    }
                    tvDueDate.visibility = View.VISIBLE
                } else {
                    tvDueDate.visibility = View.GONE
                }
                
                if (!task.tags.isNullOrEmpty()) {
                    tvTags.text = task.tags.split(",").joinToString(" ") { "#${it.trim()}" }
                    tvTags.visibility = View.VISIBLE
                } else {
                    tvTags.visibility = View.GONE
                }

                cbCompleted.isChecked = task.completed
                
                taskView.setOnClickListener {
                    val intent = Intent(context, TaskDetailActivity::class.java).apply {
                        putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.id)
                    }
                    context.startActivity(intent)
                }

                taskView.setOnLongClickListener { 
                    onTaskLongClick?.invoke(task, taskView) ?: false
                }

                cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                    updateTaskVisuals(tvTitle, isChecked)
                }

                binding.llTasksContainer.addView(taskView)
            }
        }
        
        private fun updateTaskVisuals(textView: TextView, isCompleted: Boolean) {
            if (isCompleted) {
                textView.paintFlags = textView.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                textView.alpha = 0.5f
            } else {
                textView.paintFlags = textView.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textView.alpha = 1.0f
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<TaskGroup>() {
        override fun areItemsTheSame(oldItem: TaskGroup, newItem: TaskGroup): Boolean {
            return oldItem.listId == newItem.listId
        }

        override fun areContentsTheSame(oldItem: TaskGroup, newItem: TaskGroup): Boolean {
            return oldItem == newItem
        }
    }
}
