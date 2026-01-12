package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.databinding.ItemSubtaskBinding

// Reusing ItemSubtaskBinding as it's just text + remove button logic effectively
class TagAdapter(
  private val onDelete: (String) -> Unit
) : ListAdapter<String, TagAdapter.TagViewHolder>(TagDiffCallback()) {
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
    val binding = ItemSubtaskBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return TagViewHolder(binding)
  }
  
  override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
    holder.bind(getItem(position))
  }
  
  inner class TagViewHolder(
    private val binding: ItemSubtaskBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    
    fun bind(tag: String) {
      binding.tvSubtaskTitle.text = tag
      binding.checkboxSubtask.visibility = android.view.View.GONE // Tags don't have checkbox
      
      binding.root.setOnClickListener {
         onDelete(tag) 
      }
    }
  }
  
  class TagDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
  }
}
