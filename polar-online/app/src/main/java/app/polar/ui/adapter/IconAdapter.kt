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
  private var accentColor: Int? = null

  /** Color con el que se pinta el icono seleccionado (el color elegido para la lista). */
  fun setAccentColor(color: Int) {
    accentColor = color
    notifyItemChanged(selectedPosition)
  }
  
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
  
  private fun IconViewHolder.resolve(attr: Int): Int {
    val value = android.util.TypedValue()
    itemView.context.theme.resolveAttribute(attr, value, true)
    return value.data
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
      
      // Seleccionado: relleno más marcado, borde fino y el icono en el color de la lista
      val ctx = binding.root.context
      val density = ctx.resources.displayMetrics.density
      binding.root.strokeWidth = if (isSelected) (1.5f * density).toInt() else 0
      binding.root.setCardBackgroundColor(
        resolve(
          if (isSelected) com.google.android.material.R.attr.colorSurfaceContainerHighest
          else com.google.android.material.R.attr.colorSurfaceContainer
        )
      )
      val tint = if (isSelected) accentColor ?: resolve(com.google.android.material.R.attr.colorOnSurface)
        else resolve(com.google.android.material.R.attr.colorOnSurfaceVariant)
      binding.ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
      binding.root.isSelected = isSelected
      
      binding.root.setOnClickListener {
        val position = bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return@setOnClickListener
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
        onIconSelected(iconName)
      }
    }
  }
}
