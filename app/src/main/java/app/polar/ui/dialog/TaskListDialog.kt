package app.polar.ui.dialog

import android.app.Dialog
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
  private val onSave: (String, String) -> Unit
) : DialogFragment() {
  
  private var _binding: DialogTaskListBinding? = null
  private val binding get() = _binding!!
  
  private val availableIcons = listOf(
    "ic_list",
    "ic_folder",
    "ic_work",
    "ic_home",
    "ic_favorite",
    "ic_schedule",
    "ic_star",
    "ic_circle",
    "ic_edit",
    "ic_location",
    "ic_image",
    "ic_share",
    "ic_sort",
    "ic_chat",
    "ic_check_box",
    "ic_heart"
  )
  
  private var selectedIcon = "ic_list"
  
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = DialogTaskListBinding.inflate(LayoutInflater.from(context))
    
    // Set title and populate if editing
    if (taskList != null) {
      binding.tvDialogTitle.text = getString(R.string.edit_list)
      binding.etListTitle.setText(taskList.title)
      selectedIcon = taskList.icon
    } else {
      binding.tvDialogTitle.text = getString(R.string.create_list)
    }
    
    // Setup icon picker
    val iconAdapter = IconAdapter(availableIcons) { icon ->
      selectedIcon = icon
    }
    
    binding.recyclerIcons.apply {
      layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
      adapter = iconAdapter
    }
    
    iconAdapter.setSelectedIcon(selectedIcon)
    
    binding.btnCancel.setOnClickListener {
      dismiss()
    }
    
    binding.btnSave.setOnClickListener {
      val title = binding.etListTitle.text.toString().trim()
      if (title.isNotEmpty()) {
        onSave(title, selectedIcon)
        dismiss()
      }
    }
    
    return AlertDialog.Builder(requireContext())
      .setView(binding.root)
      .create()
  }
  
  override fun onStart() {
      super.onStart()
      dialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
  }
  
  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
