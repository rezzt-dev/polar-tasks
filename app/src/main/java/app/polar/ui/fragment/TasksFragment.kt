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
  
  private lateinit var homeTaskAdapter: app.polar.ui.adapter.HomeTaskAdapter
  private var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

  private fun setupRecyclerView() {
    taskAdapter = TaskAdapter(
      lifecycleOwner = viewLifecycleOwner,
      viewModel = viewModel,
      onCheckChanged = { task, _ -> viewModel.toggleTaskCompletion(task) },
      onItemLongClick = { task -> 
          showTaskPopupMenu(task) 
          true 
      },
      onItemClick = { task -> openTaskDetail(task) }
    )
    
    homeTaskAdapter = app.polar.ui.adapter.HomeTaskAdapter(
        onGroupMove = { _, _ -> 
            // Optional: local logic if needed, but we rely on dragDropHelper sending list to ViewModel
        },
        onTaskLongClick = { task, view ->
            showTaskPopupMenu(task, view)
            true
        }
    )
    
    binding.recyclerTasks.layoutManager = LinearLayoutManager(context)
  }
  private fun openTaskDetail(task: Task) {
        val intent = android.content.Intent(requireContext(), app.polar.ui.activity.TaskDetailActivity::class.java)
        intent.putExtra(app.polar.ui.activity.TaskDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
  }
  
  private fun observeTasks() {
      // Clean up previous observers not needed? 
      // Actually simply observing both is fine, but we only set adapter based on mode.
      
      viewModel.selectedListId.observe(viewLifecycleOwner) { listId ->
          configureMode(listId)
      }
      
      viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
          if (viewModel.selectedListId.value != -1L) {
              updateEmptyState(tasks.isEmpty())
              taskAdapter.submitList(tasks)
          }
      }
      
      viewModel.homeTaskGroups.observe(viewLifecycleOwner) { groups ->
          if (viewModel.selectedListId.value == -1L) {
              updateEmptyState(groups.isEmpty())
              homeTaskAdapter.submitList(groups)
          }
      }
  }
  
  private fun updateEmptyState(isEmpty: Boolean) {
       if (isEmpty) {
        binding.emptyState.visibility = View.VISIBLE
        binding.recyclerTasks.visibility = View.GONE
      } else {
        binding.emptyState.visibility = View.GONE
        binding.recyclerTasks.visibility = View.VISIBLE
      }
  }
  
  private fun configureMode(listId: Long) {
      // Detach existing helper
      itemTouchHelper?.attachToRecyclerView(null)
      
      if (listId == -1L) {
          // Home Mode: Group Adapter
          binding.recyclerTasks.adapter = homeTaskAdapter
          
          val dragDropHelper = app.polar.util.DragDropHelper(
            onItemMove = { from, to -> homeTaskAdapter.onItemMove(from, to) },
            onMoveFinished = {
                val groups = homeTaskAdapter.getCurrentGroups()
                viewModel.updateTaskGroupsOrder(groups)
            }
          )
          itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(dragDropHelper)
          itemTouchHelper?.attachToRecyclerView(binding.recyclerTasks)
          
      } else {
          // List Mode: Task Adapter
          binding.recyclerTasks.adapter = taskAdapter
          
          val dragDropHelper = app.polar.util.DragDropHelper(
            onItemMove = { from, to -> taskAdapter.onItemMove(from, to) },
            onMoveFinished = {
                 val tasks = taskAdapter.currentList
                    .filterIsInstance<app.polar.ui.adapter.TaskListItem.Item>()
                    .mapIndexed { index, item ->
                        item.task.copy(orderIndex = index)
                    }
                viewModel.updateTasksOrder(tasks)
            }
          )
          itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(dragDropHelper)
          itemTouchHelper?.attachToRecyclerView(binding.recyclerTasks)
      }
  }

  private fun showTaskPopupMenu(task: Task, anchorView: View? = null) {
    val view = if (anchorView != null) {
        anchorView
    } else {
        val position = taskAdapter.currentList.indexOf(app.polar.ui.adapter.TaskListItem.Item(task))
        binding.recyclerTasks.findViewHolderForAdapterPosition(position)?.itemView
    }
    
    if (view == null) return
    
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
