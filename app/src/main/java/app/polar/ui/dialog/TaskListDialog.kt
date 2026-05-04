package app.polar.ui.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
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
    "ic_image", "ic_share", "ic_sort", "ic_chat", "ic_check_box", "ic_heart"
  )

  private var selectedIcon = "ic_list"
  private var selectedColor = "#7F52FF"

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
    val iconAdapter = IconAdapter(availableIcons) { icon -> selectedIcon = icon }
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
      // Update preview circle
      binding.colorPreviewCircle.backgroundTintList = ColorStateList.valueOf(parsed)
      // Update hex label
      binding.tvColorHex.text = selectedColor.uppercase()
    }
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.setBackgroundDrawable(
      android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
