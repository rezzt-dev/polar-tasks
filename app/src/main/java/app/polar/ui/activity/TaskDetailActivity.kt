package app.polar.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
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

  private val pickImageLauncher = registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.GetContent()
  ) { uri ->
      uri?.let {
          contentResolver.takePersistableUriPermission(
              it,
              android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
          val task = currentTask ?: return@let
          viewModel.attachImage(task, it)
      }
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
    viewModel.getTaskById(taskId).observe(this) { task ->
        if (task == null) return@observe

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
        binding.cbDetailCompleted.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleTaskCompletion(task)
        }

        // Tags
        if (!task.tags.isNullOrEmpty()) {
            binding.tvDetailTags.text = task.tags.split(",").joinToString("  ") { "#${it.trim()}" }
            binding.tvDetailTags.visibility = View.VISIBLE
        } else {
            binding.tvDetailTags.visibility = View.GONE
        }

        // Creation Date
        val createFormat = SimpleDateFormat("d MMM, yyyy", Locale("es", "ES"))
        binding.tvDetailDate.text = createFormat.format(Date(task.createdAt))

        // Time Estimate Pill
        if (task.timeEstimate > 0) {
            binding.chipTimeEstimate.text = DateUtils.formatTimeEstimate(task.timeEstimate)
            binding.chipTimeEstimate.visibility = View.VISIBLE
        } else {
            binding.chipTimeEstimate.visibility = View.GONE
        }

        // Priority Pill
        if (task.priority in 1..3) {
            val priorityText = when (task.priority) {
                1 -> getString(R.string.priority_low)
                2 -> getString(R.string.priority_medium)
                3 -> getString(R.string.priority_high)
                else -> getString(R.string.priority_none)
            }
            val priorityColor = when (task.priority) {
                1 -> resolveColorAttr(R.attr.colorPriorityLow)
                2 -> resolveColorAttr(R.attr.colorPriorityMedium)
                3 -> resolveColorAttr(R.attr.colorPriorityHigh)
                else -> resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            binding.chipPriority.text = priorityText
            binding.chipPriority.setTextColor(priorityColor)
            binding.chipPriority.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(priorityColor)
            binding.chipPriority.visibility = View.VISIBLE
        } else {
            binding.chipPriority.visibility = View.GONE
        }

        binding.metaPillsContainer.visibility =
            if (task.timeEstimate > 0 || task.priority in 1..3) View.VISIBLE else View.GONE

        // Due Date Card
        if (task.dueDate != null) {
            binding.cardDueDate.visibility = View.VISIBLE
            val format = SimpleDateFormat("EEEE, d MMMM", Locale("es", "ES"))

            val dateStr = when {
                android.text.format.DateUtils.isToday(task.dueDate) -> getString(R.string.today)
                android.text.format.DateUtils.isToday(task.dueDate - 86400000L) -> getString(R.string.tomorrow)
                else -> format.format(Date(task.dueDate))
            }
            binding.tvDetailDueDate.text = dateStr.replaceFirstChar { it.uppercase() }

            // Color if overdue
            if (!task.completed && task.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(task.dueDate)) {
                binding.tvDetailDueDate.setTextColor(resolveColorAttr(R.attr.colorDateOverdue))
            } else {
                binding.tvDetailDueDate.setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnSurface))
            }

            // Recurrence info
            if (task.recurrence != "NONE") {
                binding.tvDetailRecurrence.visibility = View.VISIBLE
                val recText = when(task.recurrence) {
                    "DAILY" -> getString(R.string.recurrence_daily)
                    "WEEKLY" -> getString(R.string.recurrence_weekly)
                    "MONTHLY" -> getString(R.string.recurrence_monthly)
                    else -> ""
                }
                binding.tvDetailRecurrence.text = recText
            } else {
                binding.tvDetailRecurrence.visibility = View.GONE
            }
        } else {
            binding.cardDueDate.visibility = View.GONE
        }

        // Image attachment
        if (!task.imageUri.isNullOrEmpty()) {
            binding.containerImage.visibility = View.VISIBLE
            binding.ivTaskImage.setImageURI(android.net.Uri.parse(task.imageUri))
        } else if (!task.imagePath.isNullOrEmpty()) {
            // Came from Supabase (another device/app attached it) but not cached locally yet.
            binding.containerImage.visibility = View.VISIBLE
            lifecycleScope.launch {
                val cachedUri = viewModel.downloadAndCacheTaskImage(task)
                if (cachedUri != null) binding.ivTaskImage.setImageURI(cachedUri)
            }
        } else {
            binding.containerImage.visibility = View.GONE
        }

        // List Name
        viewModel.getTaskListById(task.listId).observe(this) { taskList ->
            binding.tvDetailListName.text = taskList?.title ?: getString(R.string.no_list)
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
          onDueDateSet = { dueDate ->
              viewModel.updateSubtask(subtask.copy(dueDate = dueDate))
              if (dueDate != null) {
                  app.polar.util.AlarmManagerHelper(this).scheduleSubtaskAlarm(subtask.id, dueDate)
              }
          }
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
            pickImageLauncher.launch("image/*")
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
                  onSave = { title, description, tags, subtaskList, dueDate, recurrence, priority, timeEstimate ->
                      viewModel.updateTask(
                          task.copy(
                              title = title,
                              description = description,
                              tags = tags,
                              dueDate = dueDate,
                              recurrence = recurrence,
                              priority = priority,
                              timeEstimate = timeEstimate
                          ),
                          subtaskList
                      )
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
    val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(subtasks: List<app.polar.data.entity.Subtask>) {
            subtasksLiveData.removeObserver(this)
            val context = this@TaskDetailActivity
            val inflater = android.view.LayoutInflater.from(context)
            val cardView = inflater.inflate(R.layout.layout_task_export_card, null)

            val tvTitle = cardView.findViewById<android.widget.TextView>(R.id.tvExportTaskTitle)
            val tvDesc = cardView.findViewById<android.widget.TextView>(R.id.tvExportTaskDesc)
            val tvPriority = cardView.findViewById<android.widget.TextView>(R.id.tvPriorityBadge)
            val llSubtasksList = cardView.findViewById<android.widget.LinearLayout>(R.id.llSubtasksList)
            val containerSubtasks = cardView.findViewById<android.widget.LinearLayout>(R.id.containerExportSubtasks)
            val llDueDate = cardView.findViewById<android.widget.LinearLayout>(R.id.llExportDueDate)
            val tvDueDate = cardView.findViewById<android.widget.TextView>(R.id.tvExportDueDate)
            val tvTags = cardView.findViewById<android.widget.TextView>(R.id.tvExportTags)

            tvTitle.text = task.title
            if (task.description.isNotBlank()) {
                tvDesc.text = task.description
                tvDesc.visibility = android.view.View.VISIBLE
            } else {
                tvDesc.visibility = android.view.View.GONE
            }

            val priorityText = when (task.priority) {
                1 -> getString(R.string.priority_low)
                2 -> getString(R.string.priority_medium)
                3 -> getString(R.string.priority_high)
                else -> getString(R.string.priority_none)
            }
            val priorityColor = when (task.priority) {
                1 -> resolveColorAttr(R.attr.colorPriorityLow)
                2 -> resolveColorAttr(R.attr.colorPriorityMedium)
                3 -> resolveColorAttr(R.attr.colorPriorityHigh)
                else -> resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            }
            tvPriority.text = priorityText
            tvPriority.backgroundTintList = android.content.res.ColorStateList.valueOf(priorityColor)
            tvPriority.setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnPrimary))

            if (subtasks.isNotEmpty()) {
                containerSubtasks.visibility = android.view.View.VISIBLE
                llSubtasksList.removeAllViews()
                subtasks.take(8).forEach { sub ->
                    val stTv = android.widget.TextView(context).apply {
                        text = "• ${sub.title}"
                        textSize = 13f
                        setPadding(0, 4, 0, 4)
                        val typedValue = android.util.TypedValue()
                        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                        setTextColor(typedValue.data)
                        alpha = 0.85f
                    }
                    llSubtasksList.addView(stTv)
                }
            } else {
                containerSubtasks.visibility = android.view.View.GONE
            }

            if (task.dueDate != null) {
                llDueDate.visibility = android.view.View.VISIBLE
                tvDueDate.text = DateUtils.formatSmartDate(task.dueDate)
            } else {
                llDueDate.visibility = android.view.View.GONE
            }

            val extractedTags = task.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (extractedTags.isNotEmpty()) {
                tvTags.visibility = android.view.View.VISIBLE
                tvTags.text = extractedTags.joinToString(" ") { "#$it" }
            } else {
                tvTags.visibility = android.view.View.GONE
            }

            val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 380f, context.resources.displayMetrics).toInt(),
                android.view.View.MeasureSpec.EXACTLY
            )
            val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            cardView.measure(widthSpec, heightSpec)
            cardView.layout(0, 0, cardView.measuredWidth, cardView.measuredHeight)

            val bitmap = android.graphics.Bitmap.createBitmap(cardView.measuredWidth, cardView.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            cardView.draw(canvas)

            try {
                val cachePath = java.io.File(context.cacheDir, "shared_images")
                cachePath.mkdirs()
                val newFile = java.io.File(cachePath, "task_export_${task.id}.png")
                val stream = java.io.FileOutputStream(newFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()

                val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "app.polar.fileprovider", newFile)
                if (contentUri != null) {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                        putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_task_as_image)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    subtasksLiveData.observe(this@TaskDetailActivity, observer)
  }
}
