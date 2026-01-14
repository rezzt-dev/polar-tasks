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
  private val onSave: (String, String, String, List<Subtask>, Long?, String) -> Unit
) : DialogFragment() {
  
  private var _binding: DialogTaskBinding? = null
  private val binding get() = _binding!!
  
  // lista mutable para gestionar las subtareas directamente
  private val subtaskList = mutableListOf<Subtask>()
  private val tagList = mutableListOf<String>()
  private var selectedDate: Long? = null
  private var selectedRecurrence: String = "NONE"
  
  private lateinit var subtaskAdapter: SubtaskAdapter
  private lateinit var tagAdapter: TagAdapter
  
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = DialogTaskBinding.inflate(LayoutInflater.from(context))
    
    if (task != null) {
      binding.tvDialogTitle.text = getString(R.string.edit_task)
      binding.etTaskTitle.setText(task.title)
      binding.etTaskDescription.setText(task.description)
      // cargar etiquetas existentes
      if (task.tags.isNotEmpty()) {
          tagList.addAll(task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() })
      }
      
      // cargar subtareas existentes
      subtaskList.addAll(existingSubtasks)
      
      selectedDate = task.dueDate
      selectedRecurrence = task.recurrence
    } else {
      binding.tvDialogTitle.text = getString(R.string.create_task)
    }
    
    updateDateText()
    updateRecurrenceText()
    
    setupSubtaskList()
    setupTagList()
    
    binding.containerDate.setOnClickListener {
        val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selectedDate ?: com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
            .build()
            
        datePicker.addOnPositiveButtonClickListener { selection ->
            // ajustar a la zona horaria local inicio del dia
            val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            utcCalendar.timeInMillis = selection
            
            val localCalendar = java.util.Calendar.getInstance()
            localCalendar.set(utcCalendar.get(java.util.Calendar.YEAR), utcCalendar.get(java.util.Calendar.MONTH), utcCalendar.get(java.util.Calendar.DAY_OF_MONTH))
            
            localCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            localCalendar.set(java.util.Calendar.MINUTE, 0)
            localCalendar.set(java.util.Calendar.SECOND, 0)
            localCalendar.set(java.util.Calendar.MILLISECOND, 0)
            
            selectedDate = localCalendar.timeInMillis
            updateDateText()
        }
        
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }
    
    binding.containerRecurrence.setOnClickListener {
        val popup = android.widget.PopupMenu(requireContext(), it)
        popup.menu.add(0, 0, 0, "No se repite")
        popup.menu.add(0, 1, 1, "Diariamente")
        popup.menu.add(0, 2, 2, "Semanalmente")
        popup.menu.add(0, 3, 3, "Mensualmente")
        
        popup.setOnMenuItemClickListener { item ->
            selectedRecurrence = when (item.itemId) {
                1 -> "DAILY"
                2 -> "WEEKLY"
                3 -> "MONTHLY"
                else -> "NONE"
            }
            updateRecurrenceText()
            true
        }
        popup.show()
    }
    
    binding.btnAddSubtask.setOnClickListener {
      val subtaskTitle = binding.etSubtaskTitle.text.toString().trim()
      if (subtaskTitle.isNotEmpty()) {
        // crear nueva subtarea temporal
        subtaskList.add(Subtask(taskId = 0, title = subtaskTitle))
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
        onSave(title, description, tagsString, subtaskList.toList(), selectedDate, selectedRecurrence)
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

  private fun updateRecurrenceText() {
      val text = when (selectedRecurrence) {
          "DAILY" -> "Se repite diariamente"
          "WEEKLY" -> "Se repite semanalmente"
          "MONTHLY" -> "Se repite mensualmente"
          else -> "No se repite"
      }
      binding.tvRecurrence.text = text
      
      if (selectedRecurrence == "NONE") {
          binding.tvRecurrence.alpha = 0.6f
      } else {
          binding.tvRecurrence.alpha = 1.0f
      }
  }
  
  private fun setupSubtaskList() {
    subtaskAdapter = SubtaskAdapter(
      onCheckChanged = { subtask, isChecked -> 
          // actualizar estado de subtarea en la lista local
          val index = subtaskList.indexOf(subtask)
          if (index != -1) {
              subtaskList[index] = subtask.copy(completed = isChecked)
          }
      },
      onDelete = { subtask -> 
          subtaskList.remove(subtask)
          updateSubtaskList()
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
    if (subtaskList.isNotEmpty()) {
      binding.recyclerSubtasks.visibility = View.VISIBLE
      subtaskAdapter.submitList(subtaskList.toList())
    } else {
      binding.recyclerSubtasks.visibility = View.GONE
    }
  }
  
  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
