package app.polar.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.Subtask
import app.polar.databinding.ActivityTaskDetailBinding
import app.polar.ui.adapter.SubtaskAdapter
import app.polar.ui.dialog.TaskDialog
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.util.DateUtils
import app.polar.util.RecurrenceFormatter
import app.polar.util.RecurrenceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TaskDetailActivity : BaseActivity() {

  private lateinit var binding: ActivityTaskDetailBinding
  private val viewModel: TaskViewModel by viewModels()
  private lateinit var subtaskAdapter: SubtaskAdapter
  private var currentTask: app.polar.data.entity.Task? = null
  private var shownListId: Long? = null
  private var shownImageUri: String? = null

  // OpenDocument (y no GetContent) para poder conservar el permiso de lectura de la imagen entre
  // reinicios: con GetContent, takePersistableUriPermission lanzaba SecurityException.
  private val pickImageLauncher = registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
  ) { uri ->
      val task = currentTask
      if (uri == null || task == null) return@registerForActivityResult
      try {
          contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      } catch (e: SecurityException) {
          // El proveedor no ofrece permisos persistentes: la imagen se verá mientras dure el acceso.
          e.printStackTrace()
      }
      viewModel.attachImage(task, uri)
  }

  companion object {
    const val EXTRA_TASK_ID = "task_id"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityTaskDetailBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
    if (taskId == -1L) {
      finish()
      return
    }

    setupToolbar()
    setupSubtaskList()
    loadTaskData(taskId)
  }

  private fun setupToolbar() {
    binding.toolbar.setNavigationOnClickListener { finish() }
    setSupportActionBar(binding.toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowTitleEnabled(false)
  }

  private fun loadTaskData(taskId: Long) {
    viewModel.observeTask(taskId).observe(this) { task ->
        // La tarea se ha eliminado definitivamente (p. ej. al vaciar la papelera): no hay nada que mostrar.
        if (task == null) {
            finish()
            return@observe
        }

        currentTask = task

        // Title
        binding.tvDetailTitle.text = task.title

        // Description
        if (task.description.isNotBlank()) {
            binding.tvDetailDescription.text = task.description
            binding.tvDetailDescription.visibility = View.VISIBLE
        } else {
            binding.tvDetailDescription.visibility = View.GONE
        }

        // Checkbox / Completion
        binding.cbDetailCompleted.setOnCheckedChangeListener(null)
        binding.cbDetailCompleted.isChecked = task.completed
        binding.cbDetailCompleted.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTaskCompletion(task, isChecked)
        }

        // Tags
        if (!task.tags.isNullOrEmpty()) {
            binding.tvDetailTags.text = task.tags.split(",").joinToString("  ") { "#${it.trim()}" }
            binding.rowTags.visibility = View.VISIBLE
        } else {
            binding.rowTags.visibility = View.GONE
        }

        // Fecha de creación como pie discreto
        val locale = Locale.getDefault()
        val createFormat = SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMy"), locale
        )
        binding.tvDetailDate.text = getString(
            R.string.created_on, createFormat.format(Date(task.createdAt)).lowercase(locale)
        )

        // Estimación
        if (task.timeEstimate > 0) {
            binding.chipTimeEstimate.text = DateUtils.formatTimeEstimate(task.timeEstimate)
            binding.rowEstimate.visibility = View.VISIBLE
        } else {
            binding.rowEstimate.visibility = View.GONE
        }

        // Priority Pill
        if (task.priority in 1..3) {
            val priorityText = when (task.priority) {
                1 -> getString(R.string.priority_label_low)
                2 -> getString(R.string.priority_label_medium)
                3 -> getString(R.string.priority_label_high)
                else -> getString(R.string.priority_none)
            }
            val priorityColor = when (task.priority) {
                1 -> resolveColorAttr(R.attr.colorPriorityLow)
                2 -> resolveColorAttr(R.attr.colorPriorityMedium)
                3 -> resolveColorAttr(R.attr.colorPriorityHigh)
                else -> resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            binding.chipPriority.text = priorityText
            binding.ivPriority.imageTintList = android.content.res.ColorStateList.valueOf(priorityColor)
            binding.rowPriority.visibility = View.VISIBLE
        } else {
            binding.rowPriority.visibility = View.GONE
        }

        // Due Date Card
        if (task.dueDate != null) {
            binding.cardDueDate.visibility = View.VISIBLE
            val format = SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale
            )

            val dateStr = when {
                android.text.format.DateUtils.isToday(task.dueDate) -> getString(R.string.today)
                android.text.format.DateUtils.isToday(task.dueDate - 86400000L) -> getString(R.string.tomorrow)
                else -> format.format(Date(task.dueDate))
            }
            val timeStr = android.text.format.DateFormat.getTimeFormat(this).format(Date(task.dueDate))
            // Toda la interfaz va en minúsculas, también las fechas que genera el sistema.
            binding.tvDetailDueDate.text = "$dateStr · $timeStr".lowercase(locale)

            // Color if overdue
            if (!task.completed && task.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(task.dueDate)) {
                binding.tvDetailDueDate.setTextColor(resolveColorAttr(R.attr.colorDateOverdue))
            } else {
                binding.tvDetailDueDate.setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnSurface))
            }

            // Recurrence info
            if (RecurrenceUtils.isRecurring(task.recurrence)) {
                binding.rowRecurrence.visibility = View.VISIBLE
                binding.tvDetailRecurrence.text = RecurrenceFormatter.describe(this, task.recurrence)
            } else {
                binding.rowRecurrence.visibility = View.GONE
            }
        } else {
            binding.cardDueDate.visibility = View.GONE
            binding.rowRecurrence.visibility = View.GONE
        }

        // Image attachment (solo se decodifica de nuevo si cambia; si ya no se puede leer, se oculta)
        if (task.imageUri != shownImageUri) {
            shownImageUri = task.imageUri
            if (!task.imageUri.isNullOrEmpty()) {
                binding.ivTaskImage.setImageURI(task.imageUri.toUri())
                binding.containerImage.visibility =
                    if (binding.ivTaskImage.drawable != null) View.VISIBLE else View.GONE
            } else {
                binding.ivTaskImage.setImageDrawable(null)
                binding.containerImage.visibility = View.GONE
            }
        }

        // List Name (solo se consulta de nuevo si la tarea cambia de lista)
        if (task.listId != shownListId) {
            shownListId = task.listId
            viewModel.getTaskListById(task.listId).observe(this) { taskList ->
                binding.tvDetailListName.text = taskList?.title ?: getString(R.string.no_list)
            }
        }
    }

    viewModel.getSubtasksForTask(taskId).observe(this) { subtasks ->
        subtaskAdapter.submitList(subtasks)
    }

    binding.btnAddSubtask.setOnClickListener {
        showAddSubtaskDialog(taskId)
    }

    lifecycleScope.launch {
        repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            launch {
                viewModel.errorMessage.collect { error ->
                    error?.let {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }
  }

  private fun showSubtaskOptionsDialog(subtask: app.polar.data.entity.Subtask) {
      app.polar.ui.dialog.SubtaskDetailBottomSheet(
          subtask = subtask,
          onEdit = { showRenameSubtaskDialog(subtask) },
          onDelete = { viewModel.deleteSubtask(subtask) },
          onDueDateSet = { dueDate -> viewModel.setSubtaskDueDate(subtask, dueDate) }
      ).show(supportFragmentManager, "SubtaskDetail")
  }

  private fun showAddSubtaskDialog(taskId: Long) {
      val dialogView = layoutInflater.inflate(R.layout.dialog_simple_input, null)
      val binding = app.polar.databinding.DialogSimpleInputBinding.bind(dialogView)

      binding.tvDialogTitle.text = getString(R.string.add_subtask_title)
      binding.etInput.hint = getString(R.string.subtask_hint)

      val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
          .setView(dialogView)
          .create()

      dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

      binding.btnCancel.setOnClickListener { dialog.dismiss() }
      binding.btnSave.setOnClickListener {
          val title = binding.etInput.text.toString().trim()
          if (title.isNotEmpty()) {
              viewModel.insertSubtask(taskId, title)
              dialog.dismiss()
          }
      }

      dialog.show()
  }

  private fun showRenameSubtaskDialog(subtask: app.polar.data.entity.Subtask) {
      val dialogView = layoutInflater.inflate(R.layout.dialog_simple_input, null)
      val binding = app.polar.databinding.DialogSimpleInputBinding.bind(dialogView)

      binding.tvDialogTitle.text = getString(R.string.edit)
      binding.etInput.setText(subtask.title)
      binding.etInput.hint = getString(R.string.subtask_hint)

      val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
          .setView(dialogView)
          .create()

      dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

      binding.btnCancel.setOnClickListener { dialog.dismiss() }
      binding.btnSave.setOnClickListener {
          val newTitle = binding.etInput.text.toString().trim()
          if (newTitle.isNotEmpty()) {
              viewModel.renameSubtask(subtask, newTitle)
              dialog.dismiss()
          }
      }

      dialog.show()
  }

  private fun setupSubtaskList() {
    subtaskAdapter = SubtaskAdapter(
      onCheckChanged = { subtask, _ -> viewModel.toggleSubtaskCompletion(subtask) },
      onDelete = { subtask -> viewModel.deleteSubtask(subtask) },
      onItemClick = { subtask -> showSubtaskOptionsDialog(subtask) }
    )
    binding.recyclerDetailSubtasks.layoutManager = LinearLayoutManager(this)
    binding.recyclerDetailSubtasks.adapter = subtaskAdapter
  }

  override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
    menuInflater.inflate(R.menu.menu_task_detail, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
        android.R.id.home -> {
            finish()
            return true
        }
        R.id.action_attach_image -> {
            pickImageLauncher.launch(arrayOf("image/*"))
            return true
        }
        R.id.action_edit_task -> {
            val task = currentTask ?: return true
            showEditTaskDialog(task)
            return true
        }
        R.id.action_export_image -> {
            val task = currentTask ?: return true
            exportTaskAsImage(task)
            return true
        }
    }
    return super.onOptionsItemSelected(item)
  }

  private fun showEditTaskDialog(task: app.polar.data.entity.Task) {
      val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
      val observer = object : androidx.lifecycle.Observer<List<Subtask>> {
          override fun onChanged(subtasks: List<Subtask>) {
              subtasksLiveData.removeObserver(this)
              TaskDialog(
                  task = task,
                  existingSubtasks = subtasks,
                  onSave = { result ->
                      viewModel.updateTask(result.applyTo(task), result.subtasks, result.touchedCompletedIds)
                  }
              ).show(supportFragmentManager, "EditTaskDialog")
          }
      }
      subtasksLiveData.observe(this, observer)
  }

  private fun resolveColorAttr(attr: Int): Int {
      val typedValue = android.util.TypedValue()
      theme.resolveAttribute(attr, typedValue, true)
      return typedValue.data
  }

  private fun exportTaskAsImage(task: app.polar.data.entity.Task) {
    // Se leen las subtareas una sola vez y se comparte la tarjeta renderizada.
    val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(subtasks: List<app.polar.data.entity.Subtask>) {
            subtasksLiveData.removeObserver(this)
            app.polar.ui.export.TaskImageExporter.share(this@TaskDetailActivity, task, subtasks)
        }
    }
    subtasksLiveData.observe(this, observer)
  }
}
