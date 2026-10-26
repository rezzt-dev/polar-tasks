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
      val popup = androidx.appcompat.widget.PopupMenu(requireContext(), it)
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
        // Una recurrente necesita fecha de vencimiento: sin ella nunca llegaría a repetirse
        if (selectedRecurrence != "NONE" && selectedDate == null) {
          selectedDate = defaultDueDate()
          isDateExplicitlySet = true
          updateDateText()
        }
        updateRecurrenceText()
        true
      }
      popup.show()
    }
    
    binding.containerPriority.setOnClickListener {
      val popup = androidx.appcompat.widget.PopupMenu(requireContext(), it)
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
      // Admite varias etiquetas separadas por comas y con o sin almohadilla.
      binding.etTag.text.toString()
        .split(",")
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() && !tagList.contains(it) }
        .distinct()
        .forEach { tagList.add(it) }
      updateTagList()
      binding.etTag.text?.clear()
    }

    // "hecho" en el teclado añade la etiqueta o la subtarea sin tener que pulsar el botón +
    binding.etTag.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
        binding.btnAddTag.performClick()
        true
      } else false
    }
    binding.etSubtaskTitle.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
        binding.btnAddSubtask.performClick()
        true
      } else false
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
      var finalDate = if (isDateExplicitlySet) selectedDate else parsedInfo.dueDate
      
      // Prioritize explicit Recurrence selection
      val finalRecurrence = if (isRecurrenceExplicitlySet) selectedRecurrence
      else if (parsedInfo.recurrence != "NONE") parsedInfo.recurrence
      else selectedRecurrence
      
      if (finalRecurrence != "NONE" && finalDate == null) finalDate = defaultDueDate()

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
      // Tamaño fijo para que el NestedScrollView pueda limitar el contenido; en pantallas
      // grandes el ancho no pasa de 520dp para que el formulario no se estire.
      val displayMetrics = resources.displayMetrics
      val maxWidth = (520 * displayMetrics.density).toInt()
      val width = minOf((displayMetrics.widthPixels * 0.92).toInt(), maxWidth)
      val maxHeight = (displayMetrics.heightPixels * 0.85).toInt()
      setLayout(width, maxHeight)
      // Al crear una tarea se empieza a escribir directamente en el título.
      if (task == null) {
        setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        binding.etTaskTitle.requestFocus()
      }
    }
  }
  
  // Hoy a la hora de notificación por defecto
  private fun defaultDueDate(): Long {
    val sharedPrefs =
      requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    return java.util.Calendar.getInstance().apply {
      set(java.util.Calendar.HOUR_OF_DAY, sharedPrefs.getInt("default_notification_hour", 7))
      set(java.util.Calendar.MINUTE, sharedPrefs.getInt("default_notification_minute", 30))
      set(java.util.Calendar.SECOND, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
  }

  private fun updateDateText() {
    if (selectedDate != null) {
      val locale = java.util.Locale.getDefault()
      val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMy")
      binding.tvDueDate.text = java.text.SimpleDateFormat(pattern, locale)
        .format(java.util.Date(selectedDate!!))
        .lowercase(locale)
      setRowValueState(binding.tvDueDate, isSet = true)
    } else {
      binding.tvDueDate.text = getString(R.string.no_due_date)
      setRowValueState(binding.tvDueDate, isSet = false)
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
    setRowValueState(binding.tvRecurrence, isSet = selectedRecurrence != "NONE")
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
      setRowValueState(binding.tvPriority, isSet = true)
    } else {
      binding.ivPriorityIcon.imageTintList = android.content.res.ColorStateList.valueOf(
        resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
      )
      setRowValueState(binding.tvPriority, isSet = false)
    }
  }

  /** Valor elegido en el color del texto; sin valor, en el color secundario del tema. */
  private fun setRowValueState(view: android.widget.TextView, isSet: Boolean) {
    view.setTextColor(
      resolveColorAttr(
        if (isSet) com.google.android.material.R.attr.colorOnSurface
        else com.google.android.material.R.attr.colorOnSurfaceVariant
      )
    )
  }

  private fun resolveColorAttr(attr: Int): Int {
    val typedValue = android.util.TypedValue()
    requireContext().theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
  }
  
  private fun setupSubtaskList() {
    subtaskAdapter = SubtaskAdapter(
      showRemoveButton = true,
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
    updateTagList()
  }

  // Las etiquetas se muestran como chips con botón de quitar.
  private fun updateTagList() {
    val group = binding.chipGroupTags
    group.removeAllViews()
    tagList.forEach { tag ->
      val chip = com.google.android.material.chip.Chip(
        requireContext(), null, R.attr.polarInputChipStyle
      ).apply {
        text = "#$tag"
        isCloseIconVisible = true
        closeIconContentDescription = getString(R.string.delete)
        setOnCloseIconClickListener {
          tagList.remove(tag)
          updateTagList()
        }
      }
      group.addView(chip)
    }
    group.visibility = if (tagList.isEmpty()) View.GONE else View.VISIBLE
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
