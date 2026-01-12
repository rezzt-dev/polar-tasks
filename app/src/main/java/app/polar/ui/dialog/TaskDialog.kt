package app.polar.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.databinding.DialogTaskBinding
import app.polar.ui.adapter.SubtaskAdapter
import app.polar.ui.adapter.TagAdapter

class TaskDialog(
  private val task: Task? = null,
  private val existingSubtasks: List<Subtask> = emptyList(),
  private val onSave: (String, String, String, List<String>, Long?) -> Unit
) : DialogFragment() {
  
  private var _binding: DialogTaskBinding? = null
  private val binding get() = _binding!!
  
  private val subtaskTitles = mutableListOf<String>()
  private val tagList = mutableListOf<String>()
  private var selectedDate: Long? = null
  
  private lateinit var subtaskAdapter: SubtaskAdapter
  private lateinit var tagAdapter: TagAdapter
  
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = DialogTaskBinding.inflate(LayoutInflater.from(context))
    
    if (task != null) {
      binding.tvDialogTitle.text = getString(R.string.edit_task)
      binding.etTaskTitle.setText(task.title)
      binding.etTaskDescription.setText(task.description)
      // Load existing tags
      if (task.tags.isNotEmpty()) {
          tagList.addAll(task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() })
      }
      
      existingSubtasks.forEach { subtask ->
        subtaskTitles.add(subtask.title)
      }
      
      selectedDate = task.dueDate
    } else {
      binding.tvDialogTitle.text = getString(R.string.create_task)
    }
    
    updateDateText()
    
    setupSubtaskList()
    setupTagList()
    
    binding.containerDate.setOnClickListener {
        val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selectedDate ?: com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
            .build()
            
        datePicker.addOnPositiveButtonClickListener { selection ->
            selectedDate = selection
            updateDateText()
        }
        
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }
    
    binding.btnAddSubtask.setOnClickListener {
      val subtaskTitle = binding.etSubtaskTitle.text.toString().trim()
      if (subtaskTitle.isNotEmpty()) {
        subtaskTitles.add(subtaskTitle)
        updateSubtaskList()
        binding.etSubtaskTitle.text?.clear()
      }
    }

    binding.btnAddTag.setOnClickListener {
        val tagText = binding.etTag.text.toString().trim()
        if (tagText.isNotEmpty() && !tagList.contains(tagText)) {
            tagList.add(tagText)
            updateTagList()
            binding.etTag.text?.clear()
        }
    }
    
    binding.btnCancel.setOnClickListener {
      dismiss()
    }
    
    binding.btnSave.setOnClickListener {
      val title = binding.etTaskTitle.text.toString().trim()
      val description = binding.etTaskDescription.text.toString().trim()
      val tagsString = tagList.joinToString(",")
      
      if (title.isNotEmpty()) {
        onSave(title, description, tagsString, subtaskTitles.toList(), selectedDate)
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
  
  private fun updateDateText() {
      if (selectedDate != null) {
          val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
          binding.tvDueDate.text = format.format(java.util.Date(selectedDate!!))
          binding.tvDueDate.alpha = 1.0f
      } else {
          binding.tvDueDate.text = "Sin fecha de finalización"
          binding.tvDueDate.alpha = 0.6f
      }
  }
  
  private fun setupSubtaskList() {
    subtaskAdapter = SubtaskAdapter(
      onCheckChanged = { _, _ -> /* Checkbox logic if needed, currently dialog only tracks titles */ },
      onDelete = { subtask -> 
          // Subtask ID in dialog is just the index
          val index = subtask.id.toInt()
          if (index in subtaskTitles.indices) {
              subtaskTitles.removeAt(index)
              updateSubtaskList()
          }
      }
    )
    binding.recyclerSubtasks.layoutManager = LinearLayoutManager(context)
    binding.recyclerSubtasks.adapter = subtaskAdapter
    updateSubtaskList()
  }
  
  private fun setupTagList() {
      tagAdapter = TagAdapter { tag ->
          tagList.remove(tag)
          updateTagList()
      }
      // Horizontal layout for tags often looks better, or vertical if "like subtasks"
      // User said "like subtasks", so vertical list is safer interpretation + easier delete
      binding.recyclerTags.layoutManager = LinearLayoutManager(context)
      binding.recyclerTags.adapter = tagAdapter
      updateTagList()
  }

  private fun updateTagList() {
      if (tagList.isNotEmpty()) {
          binding.recyclerTags.visibility = View.VISIBLE
          tagAdapter.submitList(tagList.toList())
      } else {
          binding.recyclerTags.visibility = View.GONE
      }
  }
  
  private fun updateSubtaskList() {
    val tempSubtasks = subtaskTitles.mapIndexed { index, title ->
      Subtask(id = index.toLong(), taskId = 0, title = title, completed = false)
    }
    
    if (tempSubtasks.isNotEmpty()) {
      binding.recyclerSubtasks.visibility = View.VISIBLE
      subtaskAdapter.submitList(tempSubtasks)
    } else {
      binding.recyclerSubtasks.visibility = View.GONE
    }
  }
  
  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
