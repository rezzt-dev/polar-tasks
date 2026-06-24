package app.polar.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.polar.databinding.ItemGeneratedColorGridBinding

/**
 * Muestra una lista de colores generados con su nombre y valor hexadecimal
 * dentro de una cuadrícula de 2 columnas.
 */
class GeneratedColorAdapter : RecyclerView.Adapter<GeneratedColorAdapter.ViewHolder>() {

  private val items = mutableListOf<GeneratedColorItem>()

  fun submitList(newItems: List<GeneratedColorItem>) {
    items.clear()
    items.addAll(newItems)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemGeneratedColorGridBinding.inflate(
      LayoutInflater.from(parent.context), parent, false
    )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(items[position])
  }

  override fun getItemCount(): Int = items.size

  inner class ViewHolder(private val binding: ItemGeneratedColorGridBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: GeneratedColorItem) {
      binding.tvColorName.text = item.name
      binding.tvColorHex.text = item.hex.uppercase()
      binding.colorDot.backgroundTintList = ColorStateList.valueOf(item.color)
    }
  }

  data class GeneratedColorItem(
    val name: String,
    val color: Int,
    val hex: String
  )
}
