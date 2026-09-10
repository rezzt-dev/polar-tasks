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
  private val onSave: (String, String, String, List<Subtask>, Long?, String, Int, Int, Set<Long>) -> Unit
) : DialogFragment() {

  private var _binding: DialogTaskBinding? = null
  private val binding get() = _binding!!

  // lista mutable para gestionar las subtareas directamente
  private val subtaskList = mutableListOf<Subtask>()
  // ids de subtareas existentes cuyo checkbox se tocó en esta sesión del diálogo — existingSubtasks
  // es una foto tomada al abrir el diálogo, así que solo estos ids deben pisar el valor de
  // `completed` que haya en la base de datos al guardar (ver TaskRepository.replaceSubtasksForTask).
  private val touchedCompletedIds = mutableSetOf<Long>()
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
          .setTitleText(getString(R.string.select_date_title))
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
      popup.menu.add(0, 0, 0, getString(R.string.recurrence_summary_none))
      popup.menu.add(0, 1, 1, getString(R.string.recurrence_daily))
      popup.menu.add(0, 2, 2, getString(R.string.recurrence_weekly))
      popup.menu.add(0, 3, 3, getString(R.string.recurrence_monthly))
      popup.menu.add(0, 4, 4, getString(R.string.recurrence_summary_mon_wed))
      popup.menu.add(0, 5, 5, getString(R.string.recurrence_summary_first_day_month))
      
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
      popup.menu.add(0, 0, 0, getString(R.string.priority_label_none))
      popup.menu.add(0, 1, 1, getString(R.string.priority_label_low))
      popup.menu.add(0, 2, 2, getString(R.string.priority_label_medium))
      popup.menu.add(0, 3, 3, getString(R.string.priority_label_high))
      
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
          timeEstimate,
          touchedCompletedIds.toSet()
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
          timeEstimate,
          touchedCompletedIds.toSet()
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
      binding.tvDueDate.text = getString(R.string.no_due_date)
      binding.tvDueDate.alpha = 0.6f
    }
  }
  
  private fun updateRecurrenceText() {
    val text = when (selectedRecurrence) {
      "DAILY" -> getString(R.string.recurrence_summary_daily)
      "WEEKLY" -> getString(R.string.recurrence_summary_weekly)
      "MONTHLY" -> getString(R.string.recurrence_summary_monthly)
      "MON_WED" -> getString(R.string.recurrence_summary_mon_wed)
      "FIRST_DAY_MONTH" -> getString(R.string.recurrence_summary_first_day_month)
      else -> getString(R.string.recurrence_summary_none)
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
      3 -> getString(R.string.priority_label_high)
      2 -> getString(R.string.priority_label_medium)
      1 -> getString(R.string.priority_label_low)
      else -> getString(R.string.priority_label_none)
    }
    binding.tvPriority.text = text

    val priorityColorAttr = when (selectedPriority) {
      3 -> R.attr.colorPriorityHigh
      2 -> R.attr.colorPriorityMedium
      1 -> R.attr.colorPriorityLow
      else -> null
    }

    if (priorityColorAttr != null) {
      val color = resolveColorAttr(priorityColorAttr)
      binding.ivPriorityIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
      binding.tvPriority.setTextColor(color)
      binding.tvPriority.alpha = 1.0f
    } else {
      val primaryColor = resolveColorAttr(android.R.attr.colorPrimary)
      binding.ivPriorityIcon.imageTintList = android.content.res.ColorStateList.valueOf(primaryColor)
      binding.tvPriority.setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnSurface))
      binding.tvPriority.alpha = 0.6f
    }
  }

  private fun resolveColorAttr(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    requireContext().theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
  }
  
  private fun setupSubtaskList() {
    subtaskAdapter = SubtaskAdapter(
      onCheckChanged = { subtask, isChecked ->
        val index = subtaskList.indexOf(subtask)
        if (index != -1) {
          subtaskList[index] = subtask.copy(completed = isChecked)
          if (subtask.id != 0L) touchedCompletedIds.add(subtask.id)
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
