package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.polar.databinding.ItemIconBinding

class IconAdapter(
  private val icons: List<String>,
  private val onIconSelected: (String) -> Unit
) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {
  
  private var selectedPosition = 0
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
    val binding = ItemIconBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return IconViewHolder(binding)
  }
  
  override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
    holder.bind(icons[position], position == selectedPosition)
  }
  
  override fun getItemCount() = icons.size
  
  fun setSelectedIcon(icon: String) {
    val newPosition = icons.indexOf(icon)
    if (newPosition != -1) {
      val oldPosition = selectedPosition
      selectedPosition = newPosition
      notifyItemChanged(oldPosition)
      notifyItemChanged(newPosition)
    }
  }
  
  inner class IconViewHolder(
    private val binding: ItemIconBinding
  ) : RecyclerView.ViewHolder(binding.root) {
    
    fun bind(iconName: String, isSelected: Boolean) {
      val iconResId = binding.root.context.resources.getIdentifier(
        iconName,
        "drawable",
        binding.root.context.packageName
      )
      
      if (iconResId != 0) {
        binding.ivIcon.setImageResource(iconResId)
      }
      
      // Highlight selected icon
      binding.root.alpha = if (isSelected) 1.0f else 0.5f
      binding.root.strokeWidth = if (isSelected) 4 else 0
      
      binding.root.setOnClickListener {
        val oldPosition = selectedPosition
        selectedPosition = adapterPosition
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
        onIconSelected(iconName)
      }
    }
  }
}
