package app.polar.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.data.entity.Subtask
import app.polar.databinding.ItemSubtaskBinding

class SubtaskAdapter(
  private val onCheckChanged: (Subtask, Boolean) -> Unit,
  private val onDelete: (Subtask) -> Unit = {},
  private val onItemClick: (Subtask) -> Unit = {}
) : ListAdapter<Subtask, SubtaskAdapter.SubtaskViewHolder>(SubtaskDiffCallback()) {
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
    val binding = ItemSubtaskBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return SubtaskViewHolder(binding)
  }
  
  override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
    holder.bind(getItem(position))
  }
  
  inner class SubtaskViewHolder(
    private val binding: ItemSubtaskBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    
    fun bind(subtask: Subtask) {
      binding.tvSubtaskTitle.text = subtask.title
      binding.checkboxSubtask.setOnCheckedChangeListener(null)
      binding.checkboxSubtask.isChecked = subtask.completed
      
      // Strike through completed subtasks
      if (subtask.completed) {
        binding.tvSubtaskTitle.paintFlags = binding.tvSubtaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        binding.tvSubtaskTitle.alpha = 0.5f
      } else {
        binding.tvSubtaskTitle.paintFlags = binding.tvSubtaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        binding.tvSubtaskTitle.alpha = 0.8f
      }
      
      binding.checkboxSubtask.setOnCheckedChangeListener { _, isChecked ->
        onCheckChanged(subtask, isChecked)
      }
      
      binding.root.setOnClickListener {
          onItemClick(subtask)
      }
      
      binding.root.setOnLongClickListener {
          onDelete(subtask)
          true
      }
    }
  }
  
  private class SubtaskDiffCallback : DiffUtil.ItemCallback<Subtask>() {
    override fun areItemsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
      return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
      return oldItem == newItem
    }
  }
}
