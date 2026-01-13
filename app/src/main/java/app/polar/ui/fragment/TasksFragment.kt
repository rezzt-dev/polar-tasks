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
    setupFilters()
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
        onTaskClick = { task -> openTaskDetail(task) },
        onTaskLongClick = { task, view -> 
            showTaskPopupMenu(task, view)
            true
        },
        onTaskChecked = { task, _ -> viewModel.toggleTaskCompletion(task) },
        viewModel = viewModel,
        lifecycleOwner = viewLifecycleOwner
    )
    
    binding.recyclerTasks.layoutManager = LinearLayoutManager(context)
  }

  private fun setupFilters() {
      binding.chipPending.setOnCheckedChangeListener { _, isChecked ->
          viewModel.setFilterPending(isChecked)
      }
      binding.chipOverdue.setOnCheckedChangeListener { _, isChecked ->
          viewModel.setFilterOverdue(isChecked)
      }
  }
  
  private fun updateGreeting() {
      val calendar = java.util.Calendar.getInstance()
      val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
      val greetingText = when (hour) {
          in 5..12 -> "buenos días"
          in 13..20 -> "buenas tardes"
          else -> "buenas noches"
      }
      binding.tvGreeting?.text = greetingText
  }

  // --- Wrapper for Swipe Logic ---
  private fun setupSwipeActions() {
      val simpleItemTouchCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
          0, // No drag support here for now on home screen (complex with headers)
          androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
      ) {
          override fun onMove(
              recyclerView: androidx.recyclerview.widget.RecyclerView,
              viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
              target: androidx.recyclerview.widget.RecyclerView.ViewHolder
          ): Boolean = false

          override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
              val position = viewHolder.adapterPosition
              if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
              
              // Only TaskViewHolders are swipable? 
              // We should check view type in getSwipeDirs but here we can just check class
              val adapter = binding.recyclerTasks.adapter
              
              if (adapter is app.polar.ui.adapter.HomeTaskAdapter) {
                  val item = adapter.currentList[position]
                  if (item is app.polar.ui.adapter.HomeItem.TaskItem) {
                      val task = item.task
                      if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                          // Undo deletion snackbar
                          viewModel.moveToTrash(task)
                          com.google.android.material.snackbar.Snackbar.make(binding.root, "Tarea movida a la papelera", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                              .setAction("Deshacer") {
                                  // Restore task
                                  viewModel.restoreFromTrash(task)
                              }.show()
                      } else {
                          // Complete
                          viewModel.toggleTaskCompletion(task)
                          // Adapter updates via LiveData
                      }
                  }
              } else if (adapter is TaskAdapter) {
                   // Standard list logic...
                   val item = (adapter.currentList[position] as? app.polar.ui.adapter.TaskListItem.Item)?.task
                   item?.let { task ->
                       if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                           viewModel.moveToTrash(task)
                           com.google.android.material.snackbar.Snackbar.make(binding.root, "Tarea movida a la papelera", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                              .setAction("Deshacer") {
                                  viewModel.restoreFromTrash(task)
                              }.show()
                       } else {
                           viewModel.toggleTaskCompletion(task)
                       }
                   }
              }
          }

          override fun getSwipeDirs(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int {
              if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) return 0
              return super.getSwipeDirs(recyclerView, viewHolder)
          }
      }
      
      val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(simpleItemTouchCallback)
      itemTouchHelper.attachToRecyclerView(binding.recyclerTasks)
  }

  private fun openTaskDetail(task: Task) {
        val intent = android.content.Intent(requireContext(), app.polar.ui.activity.TaskDetailActivity::class.java)
        intent.putExtra(app.polar.ui.activity.TaskDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
  }
  
  private fun observeTasks() {
      viewModel.selectedListId.observe(viewLifecycleOwner) { listId ->
          configureMode(listId)
          updateGreeting()
          
          // Force update if in home mode and we already have data
          if (listId == -1L) {
              viewModel.homeTaskGroups.value?.let { updateHomeUI(it) }
          }
      }
      
      viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
          if (viewModel.selectedListId.value != -1L) {
              updateEmptyState(tasks.isEmpty())
              taskAdapter.submitList(tasks)
          }
      }
      
      viewModel.homeTaskGroups.observe(viewLifecycleOwner) { groups ->
          if (viewModel.selectedListId.value == -1L) {
              updateHomeUI(groups)
          }
      }
  }

  private fun updateHomeUI(groups: List<app.polar.data.model.TaskGroup>) {
      val hasTasks = groups.any { it.tasks.isNotEmpty() }
      updateEmptyState(!hasTasks)
      
      // Flatten groups to items
      val items = mutableListOf<app.polar.ui.adapter.HomeItem>()
      groups.forEach { group ->
          if (group.tasks.isNotEmpty()) {
              items.add(app.polar.ui.adapter.HomeItem.Header(group.listId, group.title))
              group.tasks.forEach { task ->
                  items.add(app.polar.ui.adapter.HomeItem.TaskItem(task))
              }
          }
      }
      homeTaskAdapter.submitList(items)
  }
  
  private fun updateEmptyState(isEmpty: Boolean) {
       binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
       binding.recyclerTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE
       // Hide greeting if empty state is fully taking over? Or keep it?
       // Keep greeting is nice.
  }
  
  private fun configureMode(listId: Long) {
      if (listId == -1L) {
          // Home Mode
          binding.recyclerTasks.adapter = homeTaskAdapter
          binding.tvGreeting?.visibility = View.VISIBLE
      } else {
          // List Mode
          binding.recyclerTasks.adapter = taskAdapter
          binding.tvGreeting?.visibility = View.GONE
      }
      
      // Re-attach swipe actions (simplest way is to have one attached globally or re-configure)
      setupSwipeActions()
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
            viewModel.moveToTrash(task)
            true
          }
          else -> false
        }
      }
      show()
    }
  }
  
  private fun showEditTaskDialog(task: Task) {
    // determinar subtareas existentes. usamos un observador de un solo uso
    val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
    
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(t: List<app.polar.data.entity.Subtask>) {
            // eliminar observador para evitar que updates disparen el dialogo de nuevo
            subtasksLiveData.removeObserver(this)
            
            TaskDialog(
                task = task,
                existingSubtasks = t,
                onSave = { title, description, tags, subtaskList, dueDate, recurrence ->
                  // delegar logica al viewmodel
                  viewModel.updateTask(
                      task.copy(
                          title = title, 
                          description = description, 
                          tags = tags,
                          dueDate = dueDate,
                          recurrence = recurrence
                      ),
                      subtaskList
                  )
                }
            ).show(parentFragmentManager, "EditTaskDialog")
        }
    }
    subtasksLiveData.observe(viewLifecycleOwner, observer)
  }
  
  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
