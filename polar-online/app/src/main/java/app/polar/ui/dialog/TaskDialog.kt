package app.polar.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.Subtask
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.databinding.DialogTaskBinding
import app.polar.domain.util.ParsedTask
import app.polar.domain.util.SmartParser
import app.polar.ui.adapter.SubtaskAdapter
import app.polar.ui.viewmodel.TaskListViewModel
import app.polar.util.RecurrenceUtils
import app.polar.util.ThemeManager

/**
 * Diálogo de crear / editar tarea. El título se analiza con [SmartParser] mientras se escribe: las
 * filas de fecha, repetición, prioridad y lista muestran lo detectado, salvo las que el usuario haya
 * elegido a mano, que siempre tienen preferencia.
 *
 * @param listId lista en la que se crea la tarea, o la de [task] al editar.
 */
class TaskDialog(
  private val task: Task? = null,
  private val existingSubtasks: List<Subtask> = emptyList(),
  private val listId: Long? = task?.listId,
  private val onSave: (Result) -> Unit
) : DialogFragment() {

  /** Valores que se guardan; [listId] es null si el diálogo no conoce la lista de la tarea. */
  data class Result(
    val title: String,
    val description: String,
    val tags: String,
    val subtasks: List<Subtask>,
    val dueDate: Long?,
    val recurrence: String,
    val priority: Int,
    val timeEstimate: Int,
    val touchedCompletedIds: Set<Long>,
    val listId: Long?
  ) {
    /** [task] con los campos editados en el diálogo. */
    fun applyTo(task: Task): Task = task.copy(
      listId = listId ?: task.listId,
      title = title,
      description = description,
      tags = tags,
      dueDate = dueDate,
      recurrence = recurrence,
      priority = priority,
      timeEstimate = timeEstimate
    )
  }

  private val taskListViewModel: TaskListViewModel by activityViewModels()

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
  private var isPriorityExplicitlySet: Boolean = false
  private var selectedListId: Long? = listId
  private var isListExplicitlySet: Boolean = false
  private var taskLists: List<TaskList> = emptyList()
  // Resultado del último análisis del título; se rehace al escribir y al cargar las listas
  private var parsed: ParsedTask? = null
  
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
    
    setupSmartParsing()
    setupSubtaskList()
    setupTagList()
    
    binding.containerDate.setOnClickListener {
      val datePicker =
        com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
          .setTitleText(getString(R.string.select_date_title))
          .setSelection(
            effectiveDate()?.let(::toPickerSelection)
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
        refreshParsedRows()
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
        // Sin fecha, la recurrente empieza hoy (ver effectiveDate)
        refreshParsedRows()
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
        isPriorityExplicitlySet = true
        refreshParsedRows()
        true
      }
      popup.show()
    }
    
    binding.containerList.setOnClickListener {
      val popup = androidx.appcompat.widget.PopupMenu(requireContext(), it)
      taskLists.forEachIndexed { index, list -> popup.menu.add(0, index, index, list.title) }
      popup.setOnMenuItemClickListener { item ->
        selectedListId = taskLists[item.itemId].id
        isListExplicitlySet = true
        refreshParsedRows()
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
      if (rawTitle.isBlank()) return@setOnClickListener
      val parsedInfo = parseTitle()

      // Añadir las etiquetas escritas en el título a las de los chips (sin duplicados)
      parsedInfo.tags.forEach { tag ->
        if (!tagList.contains(tag)) tagList.add(tag)
      }

      onSave(
        Result(
          // Si el título solo tenía expresiones ("mañana"), se guarda tal cual
          title = parsedInfo.title.ifEmpty { rawTitle.trim() },
          description = binding.etTaskDescription.text.toString().trim(),
          tags = tagList.joinToString(","),
          subtasks = subtaskList.toList(),
          dueDate = effectiveDate(),
          recurrence = effectiveRecurrence(),
          priority = effectivePriority(),
          timeEstimate = binding.etTimeEstimate.text.toString().toIntOrNull() ?: 0,
          touchedCompletedIds = touchedCompletedIds.toSet(),
          listId = effectiveListId()
        )
      )
      dismiss()
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

  // ---------------------------------------------------------------------------------------------
  // Lenguaje natural en el título
  // ---------------------------------------------------------------------------------------------

  private fun setupSmartParsing() {
    binding.etTaskTitle.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
      override fun afterTextChanged(s: Editable?) = refreshParsedRows()
    })
    // Las listas llegan de Room de forma asíncrona: hasta entonces "@lista" no se resuelve
    taskListViewModel.allTaskLists.observe(this) { lists ->
      taskLists = lists
      refreshParsedRows()
    }
    refreshParsedRows()
  }

  private fun parseTitle(): ParsedTask {
    val sharedPrefs =
      requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    return SmartParser.parse(
      input = binding.etTaskTitle.text.toString(),
      defaultDate = selectedDate,
      defaultHour = sharedPrefs.getInt("default_notification_hour", 7),
      defaultMinute = sharedPrefs.getInt("default_notification_minute", 30),
      language = ThemeManager.loadAppLocale(requireContext()).language,
      lists = taskLists.associate { it.id to it.title }
    )
  }

  /** Vuelve a analizar el título y pinta las filas con los valores que se guardarían. */
  private fun refreshParsedRows() {
    val binding = _binding ?: return
    parsed = parseTitle()
    updateDateText()
    updateRecurrenceText()
    updatePriorityText()
    updateListRow()
    updateSmartHint()
    binding.btnSave.isEnabled = binding.etTaskTitle.text.isNotBlank()
  }

  // Lo elegido a mano manda; si no, lo escrito en el título; si no, el valor actual de la tarea.
  // El parser ya devuelve selectedDate cuando el título no indica fecha ni hora.
  private fun effectiveDate(): Long? {
    val parsedDate = parsed?.dueDate
    val date = when {
      !isDateExplicitlySet -> parsedDate ?: selectedDate
      // Con fecha elegida a mano, del título solo se aplica la hora ("a las 17") de ese mismo día
      parsedDate != null &&
        selectedDate?.let(RecurrenceUtils::startOfDay) == RecurrenceUtils.startOfDay(parsedDate) -> parsedDate
      else -> selectedDate
    }
    // Una recurrente necesita fecha de vencimiento: sin ella nunca llegaría a repetirse
    return date ?: if (effectiveRecurrence() != "NONE") defaultDueDate() else null
  }

  private fun effectiveRecurrence(): String =
    if (isRecurrenceExplicitlySet) selectedRecurrence
    else parsed?.recurrence?.takeIf { it != "NONE" } ?: selectedRecurrence

  private fun effectivePriority(): Int =
    if (isPriorityExplicitlySet) selectedPriority else parsed?.priority ?: selectedPriority

  private fun effectiveListId(): Long? =
    if (isListExplicitlySet) selectedListId else parsed?.listId ?: selectedListId

  /** Fecha local -> medianoche UTC del mismo día, que es lo que espera MaterialDatePicker. */
  private fun toPickerSelection(localMillis: Long): Long {
    val local = java.util.Calendar.getInstance().apply { timeInMillis = localMillis }
    return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(
        local.get(java.util.Calendar.YEAR),
        local.get(java.util.Calendar.MONTH),
        local.get(java.util.Calendar.DAY_OF_MONTH)
      )
    }.timeInMillis
  }

  // Al crear, un ejemplo mientras el título está vacío; si "@lista" no existe, el aviso
  private fun updateSmartHint() {
    val unknownList = parsed?.unknownList
    val hint = binding.tvSmartHint
    when {
      unknownList != null -> {
        hint.text = getString(R.string.smart_parser_unknown_list, unknownList)
        hint.setTextColor(resolveColorAttr(R.attr.colorError))
        hint.visibility = View.VISIBLE
      }
      task == null && binding.etTaskTitle.text.isEmpty() -> {
        hint.text = getString(R.string.smart_parser_hint)
        hint.setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        hint.visibility = View.VISIBLE
      }
      else -> hint.visibility = View.GONE
    }
  }

  private fun updateListRow() {
    val list = taskLists.find { it.id == effectiveListId() }
    // Solo tiene sentido elegir lista si hay otra a la que mover la tarea
    binding.containerList.visibility =
      if (list != null && taskLists.size > 1) View.VISIBLE else View.GONE
    if (list == null) return
    binding.tvList.text = list.title
    val iconRes = resources.getIdentifier(list.icon, "drawable", requireContext().packageName)
    binding.ivListIcon.setImageResource(if (iconRes != 0) iconRes else R.drawable.ic_list)
    setRowValueState(binding.tvList, isSet = true)
  }

  private fun updateDateText() {
    val date = effectiveDate()
    if (date != null) {
      val locale = java.util.Locale.getDefault()
      val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMy")
      binding.tvDueDate.text = java.text.SimpleDateFormat(pattern, locale)
        .format(java.util.Date(date))
        .lowercase(locale)
      setRowValueState(binding.tvDueDate, isSet = true)
    } else {
      binding.tvDueDate.text = getString(R.string.no_due_date)
      setRowValueState(binding.tvDueDate, isSet = false)
    }
  }
  
  private fun updateRecurrenceText() {
    val recurrence = effectiveRecurrence()
    val text = when (recurrence) {
      "DAILY" -> getString(R.string.recurrence_summary_daily)
      "WEEKLY" -> getString(R.string.recurrence_summary_weekly)
      "MONTHLY" -> getString(R.string.recurrence_summary_monthly)
      "MON_WED" -> getString(R.string.recurrence_summary_mon_wed)
      "FIRST_DAY_MONTH" -> getString(R.string.recurrence_summary_first_day_month)
      else -> getString(R.string.recurrence_summary_none)
    }
    binding.tvRecurrence.text = text
    setRowValueState(binding.tvRecurrence, isSet = recurrence != "NONE")
  }
  
  private fun updatePriorityText() {
    val priority = effectivePriority()
    val text = when (priority) {
      3 -> getString(R.string.priority_label_high)
      2 -> getString(R.string.priority_label_medium)
      1 -> getString(R.string.priority_label_low)
      else -> getString(R.string.priority_label_none)
    }
    binding.tvPriority.text = text

    val priorityColorAttr = when (priority) {
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
