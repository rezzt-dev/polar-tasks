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


sealed class TrashItem {
    data class DeletedTask(val task: Task) : TrashItem()
    data class DeletedReminder(val reminder: app.polar.data.entity.Reminder) : TrashItem()
}

class TrashAdapter(
    private val onItemClick: (TrashItem) -> Unit
) : ListAdapter<TrashItem, TrashAdapter.TrashViewHolder>(TrashDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trash, parent, false)
        return TrashViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrashViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
        private val tvDueDate: TextView = itemView.findViewById(R.id.tvTaskDate)
        // CheckBox and Tags are not present in item_trash.xml

        fun bind(item: TrashItem) {
            when (item) {
                is TrashItem.DeletedTask -> bindTask(item.task)
                is TrashItem.DeletedReminder -> bindReminder(item.reminder)
            }
            
            itemView.setOnClickListener { onItemClick(item) }
        }

        private fun bindTask(task: Task) {
            tvTitle.text = task.title
            tvTitle.alpha = 0.6f 

            if (task.description.isNotBlank()) {
                tvDescription.text = task.description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }
            
            if (task.dueDate != null) {
                val format = SimpleDateFormat("MMM dd", Locale.getDefault())
                tvDueDate.text = format.format(Date(task.dueDate))
                tvDueDate.visibility = View.VISIBLE
            } else {
                tvDueDate.visibility = View.GONE
            }
        }

        private fun bindReminder(reminder: app.polar.data.entity.Reminder) {
            tvTitle.text = reminder.title
            tvTitle.alpha = 0.6f

            if (reminder.description.isNotBlank()) {
                tvDescription.text = reminder.description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }

            val format = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
            tvDueDate.text = format.format(Date(reminder.dateTime))
            tvDueDate.visibility = View.VISIBLE
        }
    }

    class TrashDiffCallback : DiffUtil.ItemCallback<TrashItem>() {
        override fun areItemsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean {
             return when {
                 oldItem is TrashItem.DeletedTask && newItem is TrashItem.DeletedTask -> oldItem.task.id == newItem.task.id
                 oldItem is TrashItem.DeletedReminder && newItem is TrashItem.DeletedReminder -> oldItem.reminder.id == newItem.reminder.id
                 else -> false
             }
        }
        override fun areContentsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean = oldItem == newItem
    }
}
