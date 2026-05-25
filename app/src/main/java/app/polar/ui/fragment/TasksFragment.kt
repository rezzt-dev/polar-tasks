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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.domain.model.SortMode

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TasksFragment : Fragment() {
  private var _binding: FragmentTasksBinding? = null
  private val binding get() = _binding!!
  
  private val viewModel: TaskViewModel by activityViewModels()
  private lateinit var taskAdapter: TaskAdapter
  private val completionJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()
  
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
    setupSortButton()
    // setupSwipeActions must be called once here, NOT inside configureMode().
    // Calling it there created a new ItemTouchHelper on every navigation change,
    // stacking multiple touch handlers on the same RecyclerView.
    setupSwipeActions()
    observeTasks()
  }
  
  private lateinit var homeTaskAdapter: app.polar.ui.adapter.HomeTaskAdapter
  private var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

  private fun setupRecyclerView() {
    taskAdapter = TaskAdapter(
      lifecycleOwner = viewLifecycleOwner,
      viewModel = viewModel,
      onCheckChanged = { task, isChecked, view -> handleTaskCompletion(task, isChecked, view) },
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
        onTaskChecked = { task, isChecked, view -> handleTaskCompletion(task, isChecked, view) },
        viewModel = viewModel,
        lifecycleOwner = viewLifecycleOwner
    )
    
    binding.recyclerTasks.layoutManager = LinearLayoutManager(context)
    binding.recyclerTasks.itemAnimator = null // Disable animations for instant, fluid updates
  }

  private fun setupFilters() {
      binding.chipToday.setOnCheckedChangeListener { _, isChecked ->
          viewModel.setFilterToday(isChecked)
      }
      binding.chipPending.setOnCheckedChangeListener { _, isChecked ->
          viewModel.setFilterPending(isChecked)
      }
      binding.chipOverdue.setOnCheckedChangeListener { _, isChecked ->
          viewModel.setFilterOverdue(isChecked)
      }
      binding.chipRecurrent.setOnCheckedChangeListener { _, isChecked ->
          viewModel.setFilterRecurrent(isChecked)
      }
  }

  private fun setupSortButton() {
      binding.btnSort.setOnClickListener { anchor ->
          PopupMenu(requireContext(), anchor).apply {
              menuInflater.inflate(R.menu.menu_sort, menu)
              setOnMenuItemClickListener { item ->
                  when (item.itemId) {
                      R.id.action_sort_default -> {
                          viewModel.setSortMode(SortMode.DEFAULT)
                          true
                      }
                      R.id.action_sort_unmark_first -> {
                          viewModel.setSortMode(SortMode.UNMARK_FIRST)
                          true
                      }
                      else -> false
                  }
              }
              show()
          }
      }
  }

  private fun updateSortIndicator(mode: SortMode) {
      binding.chipSortIndicator.visibility = View.GONE
  }
  
  private fun updateGreeting() {
      val calendar = java.util.Calendar.getInstance()
      val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
      val greetingText = when (hour) {
          in 5..12 -> getString(R.string.good_morning)
          in 13..20 -> getString(R.string.good_afternoon)
          else -> getString(R.string.good_night)
      }
      binding.tvGreeting?.text = greetingText
  }

  private fun handleTaskCompletion(task: Task, isChecked: Boolean, view: android.view.View) {
       // Cancel any pending completion job for this task
       completionJobs[task.id]?.cancel()
       completionJobs.remove(task.id)

       // Reset view state immediately to avoid stale alpha / translationX from swipe
       view.alpha = 1.0f
       view.translationX = 0f

       if (isChecked) {
           viewModel.setTaskCompletion(task, true)
       } else {
           viewModel.setTaskCompletion(task, false)
       }
  }

  // --- Wrapper for Swipe Logic ---
  private fun setupSwipeActions() {
      var draggedListId: Long? = null // Track which group is being dragged
      
      val simpleItemTouchCallback = object : androidx.recyclerview.widget.ItemTouchHelper.Callback() {
          
           override fun getMovementFlags(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int {
               if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) {
                   // Allow drag up/down for Headers
                   return makeMovementFlags(androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0)
               }
               // Allow swipe left/right for Tasks
               return makeMovementFlags(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT)
           }

          override fun onMove(
              recyclerView: androidx.recyclerview.widget.RecyclerView,
              viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
              target: androidx.recyclerview.widget.RecyclerView.ViewHolder
          ): Boolean {
              // Allow moving Headers
              if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder && 
                  target is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) {
                  
                  val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter ?: return false
                  val list = adapter.currentList.toMutableList() // Create mutable copy
                  val fromPos = viewHolder.bindingAdapterPosition
                  val toPos = target.bindingAdapterPosition
                  
                  if (fromPos != -1 && toPos != -1 && fromPos < list.size && toPos < list.size) {
                      // We need to move the entire GROUP (Header + Tasks), not just the header
                      
                      // 1. Identify the range of the Dragged Group
                      val fromStart = fromPos
                      var fromEnd = fromStart
                      while (fromEnd + 1 < list.size && list[fromEnd + 1] is app.polar.ui.adapter.HomeItem.TaskItem) {
                          fromEnd++
                      }
                      
                      // 2. Identify the range of the Target Group
                      // Note: 'target' is just the header we are hovering over.
                      // If moving down (from < to), we consider target group as the one starting at toPos? 
                      // Yes, usually we swap with that group.
                      val toStart = toPos
                      var toEnd = toStart
                      while (toEnd + 1 < list.size && list[toEnd + 1] is app.polar.ui.adapter.HomeItem.TaskItem) {
                          toEnd++
                      }
                      
                      // 3. Perform the move
                      // We want to move the block [fromStart..fromEnd] to the position of [toStart..toEnd]
                      
                      // Check overlap just in case (shouldn't happen with headers unless empty groups close together)
                      if (fromEnd < toStart || toEnd < fromStart) {
                           // Remove from old position (careful with indices shifts)
                           // It's easier to remove then insert
                           val groupToMove = list.subList(fromStart, fromEnd + 1).toList()
                           list.subList(fromStart, fromEnd + 1).clear()
                           
                           // Calculate new insertion index
                           var insertIndex = toStart
                           if (fromStart < toStart) {
                               // Moving Down: Insert AFTER the target group
                               // Since we removed the dragging group (from earlier in list), target indices shifted by -size
                               // Target group end was toEnd. New end is toEnd - groupToMove.size
                               // We want to insert after that: (toEnd - groupToMove.size) + 1
                               insertIndex = toEnd - groupToMove.size + 1
                           }
                           
                           list.addAll(insertIndex, groupToMove)
                           
                           // Visual Update
                           // Disable animations temporarily for smoother experience
                           val animator = recyclerView.itemAnimator
                           recyclerView.itemAnimator = null
                           
                           adapter.submitList(list) {
                               // Re-enable animations after submit
                               recyclerView.itemAnimator = animator
                           }
                           
                           return true
                      }
                  }
              }
              return false
          }

           override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
               val position = viewHolder.bindingAdapterPosition
               if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
               
               val adapter = binding.recyclerTasks.adapter
               if (adapter is app.polar.ui.adapter.HomeTaskAdapter) {
                   val item = adapter.currentList.getOrNull(position)
                   if (item is app.polar.ui.adapter.HomeItem.TaskItem) {
                       val task = item.task
                       if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                           viewModel.moveToTrash(task)
                           com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.task_moved_trash), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                               .setAction(getString(R.string.undo)) { viewModel.restoreFromTrash(task) }.show()
                       } else {
                           viewModel.toggleTaskCompletion(task)
                       }
                   }
               } else if (adapter is TaskAdapter) {
                    val item = (adapter.currentList.getOrNull(position) as? app.polar.ui.adapter.TaskListItem.Item)?.task
                    if (item != null) {
                        if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                            viewModel.moveToTrash(item)
                            com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.task_moved_trash), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.undo)) { viewModel.restoreFromTrash(item) }.show()
                        } else {
                            viewModel.toggleTaskCompletion(item)
                        }
                    }
               }
           }
          
          override fun onChildDraw(
              c: android.graphics.Canvas,
              recyclerView: androidx.recyclerview.widget.RecyclerView,
              viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
              dX: Float,
              dY: Float,
              actionState: Int,
              isCurrentlyActive: Boolean
          ) {
              if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG &&
                  viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder &&
                  draggedListId != null) {
                  
                  // Move only items from the dragged group
                  val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter
                  if (adapter != null) {
                      val items = adapter.currentList
                      
                      // Find and move all items with the dragged listId
                      for (i in items.indices) {
                          val item = items[i]
                          val shouldMove = when (item) {
                              is app.polar.ui.adapter.HomeItem.Header -> item.listId == draggedListId
                              is app.polar.ui.adapter.HomeItem.TaskItem -> {
                                  // Check if this task belongs to the dragged group
                                  // Find the header before this task
                                  var headerListId: Long? = null
                                  for (j in i downTo 0) {
                                      if (items[j] is app.polar.ui.adapter.HomeItem.Header) {
                                          headerListId = (items[j] as app.polar.ui.adapter.HomeItem.Header).listId
                                          break
                                      }
                                  }
                                  headerListId == draggedListId
                              }
                          }
                          
                          if (shouldMove) {
                              val vh = binding.recyclerTasks.findViewHolderForAdapterPosition(i)
                              vh?.itemView?.translationY = dY
                          }
                      }
                      return
                  }
              }
              
              super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
          }
          
          override fun onSelectedChanged(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?, actionState: Int) {
               super.onSelectedChanged(viewHolder, actionState)
               
               if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                   if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) {
                       // Store the listId of the group being dragged
                       val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter
                       if (adapter != null) {
                           val position = viewHolder.bindingAdapterPosition
                           if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                               val items = adapter.currentList
                               val headerItem = items[position] as? app.polar.ui.adapter.HomeItem.Header
                               draggedListId = headerItem?.listId
                               
                               if (draggedListId != null) {
                                   // Make all items in the group semi-transparent
                                   for (i in items.indices) {
                                       val item = items[i]
                                       val shouldFade = when (item) {
                                           is app.polar.ui.adapter.HomeItem.Header -> item.listId == draggedListId
                                           is app.polar.ui.adapter.HomeItem.TaskItem -> {
                                               var headerListId: Long? = null
                                               for (j in i downTo 0) {
                                                   if (items[j] is app.polar.ui.adapter.HomeItem.Header) {
                                                       headerListId = (items[j] as app.polar.ui.adapter.HomeItem.Header).listId
                                                       break
                                                   }
                                               }
                                               headerListId == draggedListId
                                           }
                                       }
                                       
                                       if (shouldFade) {
                                           val vh = binding.recyclerTasks.findViewHolderForAdapterPosition(i)
                                           vh?.itemView?.alpha = 0.5f
                                       }
                                   }
                                   return
                               }
                           }
                       }
                   }
                   viewHolder?.itemView?.alpha = 0.5f
               }
          }
          
          override fun clearView(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
               super.clearView(recyclerView, viewHolder)
               
               // Clear the dragged listId
               draggedListId = null
               
               // Restore alpha and translation for all items
               if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) {
                   // Reset ALL items in the recycler view with smooth animation
                   for (i in 0 until recyclerView.childCount) {
                       val child = recyclerView.getChildAt(i)
                       child?.animate()
                           ?.alpha(1.0f)
                           ?.translationY(0f)
                           ?.setDuration(150)
                           ?.start()
                   }
                   
                   // Persist Order
                   val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter ?: return
                   val items = adapter.currentList
                   
                   // Extract Headers order
                   val headers = items.filterIsInstance<app.polar.ui.adapter.HomeItem.Header>()
                   
                   // Map ListId -> NewIndex
                   val orderMap = headers.mapIndexed { index, header -> header.listId to index }.toMap()
                   
                   // Update ViewModel
                   val currentGroups = viewModel.homeTaskGroups.value ?: return
                   val sortedGroups = currentGroups.sortedBy { orderMap[it.listId] ?: Int.MAX_VALUE }
                   
                   viewModel.updateTaskGroupsOrder(sortedGroups)
               } else {
                   viewHolder.itemView.animate()
                       .alpha(1.0f)
                       .translationY(0f)
                       .setDuration(150)
                       .start()
               }
          }
      }
      
      itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(simpleItemTouchCallback)
      itemTouchHelper?.attachToRecyclerView(binding.recyclerTasks)
  }


  private fun openTaskDetail(task: Task) {
        val intent = android.content.Intent(requireContext(), app.polar.ui.activity.TaskDetailActivity::class.java)
        intent.putExtra(app.polar.ui.activity.TaskDetailActivity.EXTRA_TASK_ID, task.id)
        startActivity(intent)
  }
  
  private fun observeTasks() {
      // Observe StateFlows
      viewLifecycleOwner.lifecycleScope.launch {
          viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
              launch {
                  viewModel.selectedListId.collect { listId ->
                      configureMode(listId)
                      updateGreeting()
                      
                      // Force update if in home mode and we already have data
                      if (listId == -1L) {
                          viewModel.homeTaskGroups.value?.let { updateHomeUI(it) }
                      }
                  }
              }
              
              launch {
                  viewModel.tasks.collect { tasks ->
                      if (viewModel.selectedListId.value != -1L) {
                          updateEmptyState(tasks.isEmpty())
                          taskAdapter.submitList(tasks)
                      }
                  }
              }
              
              launch {
                  viewModel.listProgress.collect { progress ->
                      updateListProgress(progress)
                  }
              }
              
              launch {
                  viewModel.filterToday.collect { isChecked ->
                       if (binding.chipToday.isChecked != isChecked) {
                           binding.chipToday.isChecked = isChecked
                       }
                  }
              }

              launch {
                  viewModel.filterPending.collect { isChecked ->
                       if (binding.chipPending.isChecked != isChecked) {
                           binding.chipPending.isChecked = isChecked
                       }
                  }
              }

              launch {
                  viewModel.filterOverdue.collect { isChecked ->
                       if (binding.chipOverdue.isChecked != isChecked) {
                           binding.chipOverdue.isChecked = isChecked
                       }
                  }
              }

              launch {
                  viewModel.filterRecurrent.collect { isChecked ->
                       if (binding.chipRecurrent.isChecked != isChecked) {
                           binding.chipRecurrent.isChecked = isChecked
                       }
                  }
              }

              launch {
                  viewModel.sortMode.collect { mode ->
                      updateSortIndicator(mode)
                      // Accessibility announcement
                      if (mode == SortMode.UNMARK_FIRST) {
                          binding.root.announceForAccessibility(getString(R.string.sort_unmark_first))
                      }
                  }
              }
          }
      }
      
      // Observe LiveData (HomeTaskGroups & Error)
      // Note: Do NOT guard with selectedListId here – the LiveData can fire before selectedListId
      // is updated, causing the home screen to get stuck blank after a task completion.
      viewModel.homeTaskGroups.observe(viewLifecycleOwner) { groups ->
          updateHomeUI(groups)
      }

      viewLifecycleOwner.lifecycleScope.launch {
          viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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

  private fun updateHomeUI(groups: List<app.polar.data.model.TaskGroup>) {
      // Only update the home RecyclerView when actually in home mode
      if (viewModel.selectedListId.value != -1L) return

      val hasTasks = groups.any { it.tasks.isNotEmpty() }
      updateEmptyState(!hasTasks)
      
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
  
  private fun updateListProgress(progress: app.polar.ui.viewmodel.TaskViewModel.ListProgress) {
      val isListMode = viewModel.selectedListId.value?.let { it > 0 } == true
      if (!isListMode || progress.total == 0) {
          binding.progressContainer.visibility = View.GONE
          return
      }
      binding.progressContainer.visibility = View.VISIBLE
      binding.progressBar.setProgress(progress.percent, true)
      binding.tvProgressText.text = "${progress.completed}/${progress.total} (${progress.percent}%)"
  }

  private fun configureMode(listId: Long) {
      if (listId == -1L) {
          // Home Mode
          binding.recyclerTasks.adapter = homeTaskAdapter
          binding.tvGreeting?.visibility = View.VISIBLE
          binding.chipGroupFilters.visibility = View.GONE
          binding.progressContainer.visibility = View.GONE
          binding.btnSort.visibility = View.GONE
          binding.chipSortIndicator.visibility = View.GONE
      } else {
          // List Mode
          binding.recyclerTasks.adapter = taskAdapter
          binding.tvGreeting?.visibility = View.GONE
          binding.chipGroupFilters.visibility = View.VISIBLE
          binding.btnSort.visibility = View.VISIBLE
          updateSortIndicator(viewModel.sortMode.value)
          // Progress visibility will be handled by updateListProgress observation
      }
      binding.recyclerTasks.itemAnimator = null // Always keep animations off
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
          R.id.action_export -> {
            exportTaskToClipboard(task)
            true
          }
          R.id.action_export_image -> {
            exportTaskAsImage(task)
            true
          }
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

  private fun exportTaskAsImage(task: Task) {
    val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(subtasks: List<app.polar.data.entity.Subtask>) {
            subtasksLiveData.removeObserver(this)
            val context = requireContext()
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

            val (priorityText, priorityColor) = when (task.priority) {
                1 -> getString(R.string.priority_low) to "#2196F3"
                2 -> getString(R.string.priority_medium) to "#FF9800"
                3 -> getString(R.string.priority_high) to "#F44336"
                else -> getString(R.string.priority_none) to "#9E9E9E"
            }
            tvPriority.text = priorityText
            tvPriority.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(priorityColor))
            tvPriority.setTextColor(android.graphics.Color.WHITE)

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
                tvDueDate.text = app.polar.util.DateUtils.formatSmartDate(task.dueDate)
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
    subtasksLiveData.observe(viewLifecycleOwner, observer)
  }

  private fun exportTaskToClipboard(task: Task) {
    val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(subtasks: List<app.polar.data.entity.Subtask>) {
            subtasksLiveData.removeObserver(this)

            val sb = StringBuilder()
            sb.appendLine(task.title)
            if (task.description.isNotBlank()) {
                sb.appendLine(task.description)
            }

            if (subtasks.isNotEmpty()) {
                sb.appendLine()
                subtasks.forEach { subtask ->
                    sb.appendLine("- ${subtask.title}")
                }
            }

            if (task.dueDate != null) {
                sb.appendLine()
                val formattedDate = app.polar.util.DateUtils.formatSmartDate(task.dueDate)
                sb.appendLine("fecha: $formattedDate")
            }

            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(task.title, sb.toString().trimEnd())
            clipboard.setPrimaryClip(clip)

            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.task_copied),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    subtasksLiveData.observe(viewLifecycleOwner, observer)
  }
  
  private fun showEditTaskDialog(task: Task) {
    // Determine existing subtasks using a single-shot observer
    val subtasksLiveData = viewModel.getSubtasksForTask(task.id)
    
    val observer = object : androidx.lifecycle.Observer<List<app.polar.data.entity.Subtask>> {
        override fun onChanged(t: List<app.polar.data.entity.Subtask>) {
            // Remove observer to avoid updates triggering dialog again
            subtasksLiveData.removeObserver(this)
            
            TaskDialog(
                task = task,
                existingSubtasks = t,
                onSave = { title, description, tags, subtaskList, dueDate, recurrence, priority, timeEstimate ->
                  // Delegate logic to ViewModel
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
