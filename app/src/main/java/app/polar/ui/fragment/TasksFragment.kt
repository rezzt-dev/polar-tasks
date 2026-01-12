package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.Task
import app.polar.databinding.FragmentTasksBinding
import app.polar.ui.adapter.TaskAdapter
import app.polar.ui.dialog.TaskDialog
import app.polar.ui.viewmodel.TaskViewModel

class TasksFragment : Fragment() {
  private var _binding: FragmentTasksBinding? = null
  private val binding get() = _binding!!
  
  private val viewModel: TaskViewModel by activityViewModels()
  private lateinit var taskAdapter: TaskAdapter
  
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentTasksBinding.inflate(inflater, container, false)
    return binding.root
  }
  
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    setupRecyclerView()
    observeTasks()
  }
  
  private fun setupRecyclerView() {
    taskAdapter = TaskAdapter(
      lifecycleOwner = viewLifecycleOwner,
      viewModel = viewModel,
      onCheckChanged = { task, _ ->
        viewModel.toggleTaskCompletion(task)
      },
      onItemLongClick = { task ->
        showTaskPopupMenu(task)
        true
      },
      onItemClick = { task ->
        val intent = android.content.Intent(requireContext(), app.polar.ui.activity.TaskDetailActivity::class.java)
        intent.putExtra(app.polar.ui.activity.TaskDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
      }
    )
    
    binding.recyclerTasks.apply {
      layoutManager = LinearLayoutManager(context)
      adapter = taskAdapter
    }
  }
  
  private fun observeTasks() {
    viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
      if (tasks.isEmpty()) {
        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerTasks.visibility = View.GONE
      } else {
        binding.emptyState.visibility = View.GONE
        binding.recyclerTasks.visibility = View.VISIBLE
        taskAdapter.submitList(tasks)
      }
    }
  }
  
  private fun showTaskPopupMenu(task: Task) {
    val position = taskAdapter.currentList.indexOf(app.polar.ui.adapter.TaskListItem.Item(task))
    val view = binding.recyclerTasks.findViewHolderForAdapterPosition(position)?.itemView ?: return
    
    PopupMenu(requireContext(), view).apply {
      menuInflater.inflate(R.menu.menu_task, menu)
      setOnMenuItemClickListener { item ->
        when (item.itemId) {
          R.id.action_edit -> {
            showEditTaskDialog(task)
            true
          }
          R.id.action_delete -> {
            viewModel.deleteTask(task)
            true
          }
          else -> false
        }
      }
      show()
    }
  }
  
  private fun showEditTaskDialog(task: Task) {
    // Determine existing subtasks. Using a one-shot observer correct pattern
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(t: List<app.polar.data.entity.Subtask>) {
            // Remove observer to avoid updates triggering dialog again
            viewModel.getSubtasksForTask(task.id).removeObserver(this)
            
            TaskDialog(
                task = task,
                existingSubtasks = t,
                onSave = { title, description, tags, subtaskTitles, dueDate ->
                  // Fix: Delegate logic to ViewModel
                  viewModel.updateTask(
                      task.copy(
                          title = title, 
                          description = description, 
                          tags = tags,
                          dueDate = dueDate
                      ),
                      subtaskTitles
                  )
                }
            ).show(parentFragmentManager, "EditTaskDialog")
        }
    }
    viewModel.getSubtasksForTask(task.id).observe(viewLifecycleOwner, observer)
  }
  
  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
