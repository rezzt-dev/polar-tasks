package app.polar.ui.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.TaskList
import app.polar.databinding.DialogTaskListBinding
import app.polar.ui.adapter.IconAdapter

class TaskListDialog(
  private val taskList: TaskList? = null,
  private val onSave: ((String, String, Boolean) -> Unit)? = null,
  private val onSaveWithColor: ((String, String, Boolean, String) -> Unit)? = null
) : DialogFragment() {

  private var _binding: DialogTaskListBinding? = null
  private val binding get() = _binding!!

  private val availableIcons = listOf(
    "ic_list", "ic_folder", "ic_work", "ic_home", "ic_favorite",
    "ic_schedule", "ic_star", "ic_circle", "ic_edit", "ic_location",
    "ic_image", "ic_share", "ic_sort", "ic_chat", "ic_check_box", "ic_heart",
    "ic_shopping_cart", "ic_school", "ic_menu_book", "ic_fitness_center",
    "ic_restaurant", "ic_pets", "ic_flight", "ic_directions_car",
    "ic_account_balance_wallet", "ic_savings", "ic_medical_services", "ic_family_restroom",
    "ic_music_note", "ic_movie", "ic_sports_esports", "ic_code",
    "ic_yard", "ic_cleaning_services", "ic_build", "ic_lightbulb",
    "ic_card_giftcard", "ic_beach_access", "ic_child_care", "ic_flag"
  )

  private var selectedIcon = "ic_list"
  private var selectedColor = PRESET_COLORS[7]
  private lateinit var iconAdapter: IconAdapter

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = DialogTaskListBinding.inflate(LayoutInflater.from(context))

    // Populate if editing existing list
    if (taskList != null) {
      binding.tvDialogTitle.text = getString(R.string.edit_list)
      binding.etListTitle.setText(taskList.title)
      selectedIcon = taskList.icon
      selectedColor = taskList.color
      binding.switchDependencyChain.isChecked = taskList.isDependencyChain
      binding.switchDependencyChain.isEnabled = taskList.isDependencyChain.not()
    } else {
      binding.tvDialogTitle.text = getString(R.string.create_list)
    }

    // Icon picker
    iconAdapter = IconAdapter(availableIcons) { icon ->
      selectedIcon = icon
      updateListPreview()
    }
    binding.recyclerIcons.apply {
      layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
      adapter = iconAdapter
    }
    iconAdapter.setSelectedIcon(selectedIcon)

    // Color picker — initialise with stored/default color
    val picker = binding.colorPickerView
    runCatching { picker.setColor(Color.parseColor(selectedColor)) }

    picker.onColorChanged = { color ->
      selectedColor = String.format("#%06X", 0xFFFFFF and color)
      updateColorUI()
    }

    setupColorPresets()
    binding.rowCustomColor.setOnClickListener { toggleCustomColorPicker() }

    // Sync UI to initial color
    updateColorUI()

    binding.btnCancel.setOnClickListener { dismiss() }

    binding.btnSave.setOnClickListener {
      val title = binding.etListTitle.text.toString().trim()
      val isChain = binding.switchDependencyChain.isChecked
      if (title.isNotEmpty()) {
        onSaveWithColor?.invoke(title, selectedIcon, isChain, selectedColor)
          ?: onSave?.invoke(title, selectedIcon, isChain)
        dismiss()
      }
    }

    return AlertDialog.Builder(requireContext())
      .setView(binding.root)
      .create()
  }

  private fun updateColorUI() {
    runCatching {
      val parsed = Color.parseColor(selectedColor)
      binding.colorPreviewCircle.backgroundTintList = ColorStateList.valueOf(parsed)
      binding.tvColorHex.text = selectedColor.uppercase()
      iconAdapter.setAccentColor(parsed)
    }
    updateListPreview()
    refreshPresetSelection()
  }

  /** Vista previa junto al nombre: el icono elegido en el color elegido. */
  private fun updateListPreview() {
    val ctx = requireContext()
    val iconRes = ctx.resources.getIdentifier(selectedIcon, "drawable", ctx.packageName)
    if (iconRes != 0) binding.ivListPreview.setImageResource(iconRes)
    runCatching {
      binding.ivListPreview.imageTintList = ColorStateList.valueOf(Color.parseColor(selectedColor))
    }
  }

  // Paleta predefinida: círculos de color con un anillo en el seleccionado.
  private fun setupColorPresets() {
    val density = resources.displayMetrics.density
    val size = (32 * density).toInt()
    PRESET_COLORS.forEach { hex ->
      val swatch = View(requireContext()).apply {
        layoutParams = android.view.ViewGroup.MarginLayoutParams(size, size)
        tag = hex
        contentDescription = getString(R.string.color_option, hex)
        isClickable = true
        isFocusable = true
        setOnClickListener {
          selectedColor = hex
          runCatching { binding.colorPickerView.setColor(Color.parseColor(hex)) }
          updateColorUI()
        }
      }
      binding.colorPresets.addView(swatch)
    }
    refreshPresetSelection()
  }

  private fun refreshPresetSelection() {
    if (_binding == null) return
    val density = resources.displayMetrics.density
    val ringColor = resolveColor(com.google.android.material.R.attr.colorOnSurface)
    val gapColor = resolveColor(com.google.android.material.R.attr.colorSurface)
    for (i in 0 until binding.colorPresets.childCount) {
      val swatch = binding.colorPresets.getChildAt(i)
      val hex = swatch.tag as? String ?: continue
      val selected = hex.equals(selectedColor, ignoreCase = true)
      val fill = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(Color.parseColor(hex))
        // El seleccionado se marca con un anillo separado por un hueco del color de fondo.
        if (selected) setStroke((3 * density).toInt(), gapColor)
      }
      swatch.background = if (selected) {
        android.graphics.drawable.LayerDrawable(
          arrayOf(
            android.graphics.drawable.GradientDrawable().apply {
              shape = android.graphics.drawable.GradientDrawable.OVAL
              setStroke((2 * density).toInt(), ringColor)
            },
            fill
          )
        ).apply { setLayerInset(1, (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt()) }
      } else {
        fill
      }
      swatch.isSelected = selected
    }
  }

  private fun toggleCustomColorPicker() {
    val card = binding.cardColorPicker
    val expand = card.visibility != View.VISIBLE
    android.transition.TransitionManager.beginDelayedTransition(
      binding.root as android.view.ViewGroup,
      android.transition.AutoTransition().setDuration(200)
    )
    card.visibility = if (expand) View.VISIBLE else View.GONE
    binding.ivCustomColorArrow.animate().rotation(if (expand) 180f else 0f).setDuration(200).start()
  }

  private fun resolveColor(attr: Int): Int {
    val value = android.util.TypedValue()
    requireContext().theme.resolveAttribute(attr, value, true)
    return value.data
  }

  override fun onStart() {
    super.onStart()
    val win = dialog?.window ?: return

    // Transparent background so dialog_background drawable shows
    win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

    // Measure screen dimensions
    val dm = DisplayMetrics()
    @Suppress("DEPRECATION")
    win.windowManager.defaultDisplay.getMetrics(dm)

    // Width: 92 % of screen width (comfortable on phones and tablets)
    val width = (dm.widthPixels * 0.92).toInt()

    // Height: cap at 87 % of screen height so content scrolls and buttons
    // stay visible on small devices; shrinks naturally on larger screens.
    val maxHeight = (dm.heightPixels * 0.87).toInt()

    // Apply size and centre the dialog on screen
    win.setLayout(width, maxHeight)
    val lp = win.attributes
    lp.gravity = android.view.Gravity.CENTER
    win.attributes = lp
  }

  companion object {
    // Paleta neutra y equilibrada; el índice 7 (índigo) es el color por defecto de una lista nueva.
    private val PRESET_COLORS = listOf(
      "#64748B", "#EF4444", "#F97316", "#EAB308", "#22C55E",
      "#14B8A6", "#3B82F6", "#6366F1", "#A855F7", "#EC4899"
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
