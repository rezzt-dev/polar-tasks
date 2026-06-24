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
  private val onSave: (String, String, String, List<Subtask>, Long?, String, Int, Int) -> Unit
) : DialogFragment() {
  
  private var _binding: DialogTaskBinding? = null
  private val binding get() = _binding!!
  
  // lista mutable para gestionar las subtareas directamente
  private val subtaskList = mutableListOf<Subtask>()
  private val tagList = mutableListOf<String>()
  private var selectedDate: Long? = null
  private var isDateExplicitlySet: Boolean = false
  private var selectedRecurrence: String = "NONE"
  private var isRecurrenceExplicitlySet: Boolean = false
  private var selectedPriority: Int = 0
  
  private lateinit var subtaskAdapter: SubtaskAdapter
  private lateinit var tagAdapter: TagAdapter
  
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    _binding = DialogTaskBinding.inflate(LayoutInflater.from(context))
    
    if (task != null) {
      binding.tvDialogTitle.text = getString(R.string.edit_task)
      binding.etTaskTitle.setText(task.title)
      binding.etTaskDescription.setText(task.description)
      if (task.timeEstimate > 0) {
          binding.etTimeEstimate.setText(task.timeEstimate.toString())
      }
      // cargar etiquetas existentes
      if (task.tags.isNotEmpty()) {
        tagList.addAll(task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() })
      }
      
      // cargar subtareas existentes
      subtaskList.addAll(existingSubtasks)
      
      selectedDate = task.dueDate
      selectedRecurrence = task.recurrence
      selectedPriority = task.priority
    } else {
      binding.tvDialogTitle.text = getString(R.string.create_task)
    }
    
    updateDateText()
    updateRecurrenceText()
    updatePriorityText()
    
    setupSubtaskList()
    setupTagList()
    
    binding.containerDate.setOnClickListener {
      val datePicker =
        com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
          .setTitleText("select date")
          .setSelection(
            selectedDate
              ?: com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds()
          )
          .build()
      
      datePicker.addOnPositiveButtonClickListener { selection ->
        // ajustar a la zona horaria local inicio del dia
        val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = selection
        
        val localCalendar = java.util.Calendar.getInstance()
        localCalendar.set(
          utcCalendar.get(java.util.Calendar.YEAR),
          utcCalendar.get(java.util.Calendar.MONTH),
          utcCalendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        
        val sharedPrefs =
          requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val defaultHour = sharedPrefs.getInt("default_notification_hour", 7)
        val defaultMinute = sharedPrefs.getInt("default_notification_minute", 30)
        
        localCalendar.set(java.util.Calendar.HOUR_OF_DAY, defaultHour)
        localCalendar.set(java.util.Calendar.MINUTE, defaultMinute)
        localCalendar.set(java.util.Calendar.SECOND, 0)
        localCalendar.set(java.util.Calendar.MILLISECOND, 0)
        
        selectedDate = localCalendar.timeInMillis
        isDateExplicitlySet = true
        updateDateText()
      }
      
      datePicker.show(parentFragmentManager, "DATE_PICKER")
    }
    
    binding.containerRecurrence.setOnClickListener {
      val popup = android.widget.PopupMenu(requireContext(), it)
      popup.menu.add(0, 0, 0, "no se repite")
      popup.menu.add(0, 1, 1, "diariamente")
      popup.menu.add(0, 2, 2, "semanalmente")
      popup.menu.add(0, 3, 3, "mensualmente")
      popup.menu.add(0, 4, 4, "cada lunes y miercoles")
      popup.menu.add(0, 5, 5, "primer dia del mes")
      
      popup.setOnMenuItemClickListener { item ->
        selectedRecurrence = when (item.itemId) {
          1 -> "DAILY"
          2 -> "WEEKLY"
          3 -> "MONTHLY"
          4 -> "MON_WED"
          5 -> "FIRST_DAY_MONTH"
          else -> "NONE"
        }
        isRecurrenceExplicitlySet = true
        updateRecurrenceText()
        true
      }
      popup.show()
    }
    
    binding.containerPriority.setOnClickListener {
      val popup = android.widget.PopupMenu(requireContext(), it)
      popup.menu.add(0, 0, 0, "sin prioridad")
      popup.menu.add(0, 1, 1, "prioridad baja")
      popup.menu.add(0, 2, 2, "prioridad media")
      popup.menu.add(0, 3, 3, "prioridad alta")
      
      popup.setOnMenuItemClickListener { item ->
        selectedPriority = item.itemId
        updatePriorityText()
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
      val rawTitle = binding.etTaskTitle.text.toString()
      val description = binding.etTaskDescription.text.toString().trim()
      
      // Parse natural language from title
      val sharedPrefs =
        requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
      val defaultHour = sharedPrefs.getInt("default_notification_hour", 7)
      val defaultMinute = sharedPrefs.getInt("default_notification_minute", 30)
      val parsedInfo =
        app.polar.domain.util.SmartParser.parse(rawTitle, selectedDate, defaultHour, defaultMinute)
      
      // Add newly parsed tags to our tagList (prevent duplicates)
      parsedInfo.tags.forEach { tag ->
        if (!tagList.contains(tag)) tagList.add(tag)
      }
      
      val tagsString = tagList.joinToString(",")
      
      // Prioritize explicit Date selection
      val finalDate = if (isDateExplicitlySet) selectedDate else parsedInfo.dueDate
      
      // Prioritize explicit Recurrence selection
      val finalRecurrence = if (isRecurrenceExplicitlySet) selectedRecurrence
      else if (parsedInfo.recurrence != "NONE") parsedInfo.recurrence
      else selectedRecurrence
      
      val timeEstimate = binding.etTimeEstimate.text.toString().toIntOrNull() ?: 0

      if (parsedInfo.title.isNotEmpty()) {
        onSave(
          parsedInfo.title,
          description,
          tagsString,
          subtaskList.toList(),
          finalDate,
          finalRecurrence,
          selectedPriority,
          timeEstimate
        )
        dismiss()
      } else if (rawTitle.trim().isNotEmpty()) {
        onSave(
          rawTitle.trim(),
          description,
          tagsString,
          subtaskList.toList(),
          finalDate,
          finalRecurrence,
          selectedPriority,
          timeEstimate
        )
        dismiss()
      }
    }
    
    return AlertDialog.Builder(requireContext())
      .setView(binding.root)
      .create()
  }
  
  override fun onStart() {
    super.onStart()
    dialog?.window?.apply {
      setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
      // Set dialog size so ScrollView can properly constrain content
      val displayMetrics = resources.displayMetrics
      val width = (displayMetrics.widthPixels * 0.9).toInt()
      val maxHeight = (displayMetrics.heightPixels * 0.85).toInt()
      setLayout(width, maxHeight)
    }
  }
  
  private fun updateDateText() {
    if (selectedDate != null) {
      val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
      binding.tvDueDate.text = format.format(java.util.Date(selectedDate!!))
      binding.tvDueDate.alpha = 1.0f
    } else {
      binding.tvDueDate.text = "sin fecha de finalizacion"
      binding.tvDueDate.alpha = 0.6f
    }
  }
  
  private fun updateRecurrenceText() {
    val text = when (selectedRecurrence) {
      "DAILY" -> "se repite diariamente"
      "WEEKLY" -> "se repite semanalmente"
      "MONTHLY" -> "se repite mensualmente"
      "MON_WED" -> "cada lunes y miercoles"
      "FIRST_DAY_MONTH" -> "primer dia del mes"
      else -> "no se repite"
    }
    binding.tvRecurrence.text = text
    
    if (selectedRecurrence == "NONE") {
      binding.tvRecurrence.alpha = 0.6f
    } else {
      binding.tvRecurrence.alpha = 1.0f
    }
  }
  
  private fun updatePriorityText() {
    val text = when (selectedPriority) {
      3 -> "prioridad alta"
      2 -> "prioridad media"
      1 -> "prioridad baja"
      else -> "sin prioridad"
    }
    binding.tvPriority.text = text
    
    val colorStr = when (selectedPriority) {
      3 -> "#F44336" // Red
      2 -> "#FF9800" // Orange
      1 -> "#2196F3" // Blue
      else -> null
    }
    
    if (colorStr != null) {
      val color = android.graphics.Color.parseColor(colorStr)
      binding.ivPriorityIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
      binding.tvPriority.setTextColor(color)
      binding.tvPriority.alpha = 1.0f
    } else {
      val typedValue = android.util.TypedValue()
      requireContext().theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
      binding.ivPriorityIcon.imageTintList =
        android.content.res.ColorStateList.valueOf(typedValue.data)
      requireContext().theme.resolveAttribute(
        com.google.android.material.R.attr.colorOnSurface,
        typedValue,
        true
      )
      binding.tvPriority.setTextColor(typedValue.data)
      binding.tvPriority.alpha = 0.6f
    }
  }
  
  private fun setupSubtaskList() {
    subtaskAdapter = SubtaskAdapter(
      onCheckChanged = { subtask, isChecked ->
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
    binding.recyclerSubtasks.isNestedScrollingEnabled = false
    updateSubtaskList()
  }
  
  private fun setupTagList() {
    tagAdapter = TagAdapter { tag ->
      tagList.remove(tag)
      updateTagList()
    }
    binding.recyclerTags.layoutManager = LinearLayoutManager(context)
    binding.recyclerTags.adapter = tagAdapter
    binding.recyclerTags.isNestedScrollingEnabled = false
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
