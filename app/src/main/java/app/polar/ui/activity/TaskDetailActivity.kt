package app.polar.ui.activity

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.databinding.ActivityTaskDetailBinding
import app.polar.ui.adapter.SubtaskAdapter
import app.polar.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.*

class TaskDetailActivity : AppCompatActivity() {
  
  private lateinit var binding: ActivityTaskDetailBinding
  private val viewModel: TaskViewModel by viewModels()
  private lateinit var subtaskAdapter: SubtaskAdapter
  
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
        
        // Title & Description
        binding.tvDetailTitle.text = task.title
        binding.tvDetailDescription.text = task.description.ifEmpty { getString(R.string.no_tasks) } 
        
        // Checkbox / Completion
        binding.cbDetailCompleted.setOnCheckedChangeListener(null)
        binding.cbDetailCompleted.isChecked = task.completed
        binding.cbDetailCompleted.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleTaskCompletion(task)
        }
        
        // Tags
        if (!task.tags.isNullOrEmpty()) {
            binding.tvDetailTags.text = task.tags.split(",").joinToString(" ") { "#${it.trim()}" }
            binding.tvDetailTags.visibility = View.VISIBLE
        } else {
            binding.tvDetailTags.visibility = View.GONE
        }
        
        // Creation Date
        val createFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.tvDetailDate.text = createFormat.format(Date(task.createdAt))
        
        // Due Date logic
        if (task.dueDate != null) {
            binding.containerDueDate.visibility = View.VISIBLE
            val format = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            
            val dateStr = when {
                android.text.format.DateUtils.isToday(task.dueDate) -> getString(R.string.today)
                android.text.format.DateUtils.isToday(task.dueDate - 86400000L) -> getString(R.string.tomorrow)
                else -> format.format(Date(task.dueDate))
            }
            binding.tvDetailDueDate.text = dateStr.replaceFirstChar { it.uppercase() }
            
            // Color if overdue
            if (!task.completed && task.dueDate < System.currentTimeMillis() && !android.text.format.DateUtils.isToday(task.dueDate)) {
                binding.tvDetailDueDate.setTextColor(android.graphics.Color.parseColor("#B3261E"))
            } else {
                binding.tvDetailDueDate.setTextColor(getColor(android.R.color.tab_indicator_text)) 
            }
        } else {
            binding.containerDueDate.visibility = View.GONE
        }
        
        // List Name
        viewModel.getTaskListById(task.listId).observe(this) { taskList ->
            binding.tvDetailListName.text = taskList?.title ?: "No List"
        }
    }
    
    viewModel.getSubtasksForTask(taskId).observe(this) { subtasks ->
        subtaskAdapter.submitList(subtasks)
    }
    
    binding.btnAddSubtask.setOnClickListener {
        showAddSubtaskDialog(taskId)
    }
  }
  
  private fun showSubtaskOptionsDialog(subtask: app.polar.data.entity.Subtask) {
      val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
      androidx.appcompat.app.AlertDialog.Builder(this)
          .setTitle(subtask.title)
          .setItems(options) { _, which ->
              when (which) {
                  0 -> showRenameSubtaskDialog(subtask)
                  1 -> viewModel.deleteSubtask(subtask)
              }
          }
          .show()
  }
  
  private fun showAddSubtaskDialog(taskId: Long) {
      val dialogView = layoutInflater.inflate(R.layout.dialog_simple_input, null)
      val binding = app.polar.databinding.DialogSimpleInputBinding.bind(dialogView)
      
      binding.tvDialogTitle.text = getString(R.string.add_subtask)
      binding.etInput.hint = getString(R.string.subtask_hint)
      
      val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
          .setView(dialogView)
          .create()
          
      dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
      
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
          
      dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
      
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

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
        finish()
        return true
    }
    return super.onOptionsItemSelected(item)
  }
}
