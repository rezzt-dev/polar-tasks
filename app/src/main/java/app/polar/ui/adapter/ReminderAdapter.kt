package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.data.entity.Reminder
import app.polar.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReminderAdapter(
    private val onCheckChanged: (Reminder) -> Unit,
    private val onItemClick: (Reminder) -> Unit,
    private val onItemLongClick: (Reminder, android.view.View) -> Boolean
) : ListAdapter<Reminder, ReminderAdapter.ReminderViewHolder>(ReminderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = getItem(position)
        holder.bind(reminder, onCheckChanged, onItemClick, onItemLongClick)
    }

    class ReminderViewHolder(private val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            reminder: Reminder, 
            onCheckChanged: (Reminder) -> Unit, 
            onItemClick: (Reminder) -> Unit,
            onItemLongClick: (Reminder, android.view.View) -> Boolean
        ) {
            binding.tvReminderTitle.text = reminder.title
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.tvReminderTime.text = dateFormat.format(java.util.Date(reminder.dateTime))
            
            // Remove listener to avoid triggering loop
            binding.cbReminderComplete.setOnCheckedChangeListener(null)
            binding.cbReminderComplete.isChecked = reminder.isCompleted
            
            // Strike through if completed
            if (reminder.isCompleted) {
                binding.tvReminderTitle.paintFlags = binding.tvReminderTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.root.alpha = 0.5f
            } else {
                binding.tvReminderTitle.paintFlags = binding.tvReminderTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.root.alpha = 1.0f
            }

            binding.cbReminderComplete.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != reminder.isCompleted) {
                    onCheckChanged(reminder)
                }
            }
            
            binding.root.setOnClickListener { onItemClick(reminder) }
            binding.root.setOnLongClickListener { 
                onItemLongClick(reminder, it)
            }
        }
    }

    class ReminderDiffCallback : DiffUtil.ItemCallback<Reminder>() {
        override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder): Boolean = oldItem == newItem
    }
}
