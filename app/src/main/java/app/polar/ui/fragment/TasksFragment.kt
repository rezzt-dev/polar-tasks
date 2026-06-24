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
import app.polar.ui.adapter.HomeItem
import app.polar.ui.adapter.TaskAdapter
import app.polar.ui.adapter.TaskListItem
import app.polar.ui.dialog.TaskDialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import app.polar.ui.animation.TaskItemAnimator
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.domain.model.SortMode
import app.polar.util.TaskSwipeHelper

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TasksFragment : Fragment() {
  private var _binding: FragmentTasksBinding? = null
  private val binding get() = _binding!!
  
  private val viewModel: TaskViewModel by activityViewModels()
  private lateinit var taskAdapter: TaskAdapter
  private val completionJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()
  
  // Últimos datos conocidos para poder re-renderizar cuando cambie el estado
  // de expansión de tareas completadas sin esperar una nueva emisión de la BD.
  private var latestTaskItems: List<TaskListItem> = emptyList()
  private var latestHomeGroups: List<app.polar.data.model.TaskGroup> = emptyList()
  
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
      onItemClick = { task -> openTaskDetail(task) },
      onCompletedHeaderClick = { viewModel.toggleCompletedTasksExpanded() }
    )
    
    homeTaskAdapter = app.polar.ui.adapter.HomeTaskAdapter(
        onTaskClick = { task -> openTaskDetail(task) },
        onTaskLongClick = { task, view -> 
            showTaskPopupMenu(task, view)
            true
        },
        onTaskChecked = { task, isChecked, view -> handleTaskCompletion(task, isChecked, view) },
        onCompletedHeaderClick = { viewModel.toggleCompletedTasksExpanded() },
        viewModel = viewModel,
        lifecycleOwner = viewLifecycleOwner
    )
    
    binding.recyclerTasks.layoutManager = LinearLayoutManager(context)
    binding.recyclerTasks.itemAnimator = TaskItemAnimator()
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

  // --- Swipe logic with reusable TaskSwipeHelper ---
  private fun setupSwipeActions() {
      var draggedListId: Long? = null
      
      val swipeHelper = TaskSwipeHelper(
          rightSwipeConfig = TaskSwipeHelper.SwipeConfig(
              backgroundColorAttr = R.attr.colorSuccess,
              iconRes = R.drawable.ic_check
          ),
          leftSwipeConfig = TaskSwipeHelper.SwipeConfig(
              backgroundColorAttr = R.attr.colorError,
              iconRes = R.drawable.ic_trash
          ),
          getDragFlagsForHolder = { viewHolder ->
              when (viewHolder) {
                  is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder ->
                      androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN
                  else -> 0
              }
          },
          getSwipeFlagsForHolder = { viewHolder ->
              when (viewHolder) {
                  is TaskAdapter.TaskViewHolder,
                  is app.polar.ui.adapter.HomeTaskAdapter.TaskViewHolder ->
                      androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
                  else -> 0
              }
          },
          onMoveCallback = { fromPosition, toPosition ->
              handleItemMove(fromPosition, toPosition)
          },
          onSwipedRight = { position ->
              handleTaskSwipe(position, androidx.recyclerview.widget.ItemTouchHelper.RIGHT)
          },
          onSwipedLeft = { position ->
              handleTaskSwipe(position, androidx.recyclerview.widget.ItemTouchHelper.LEFT)
          },
          onSelectedChangedCallback = { viewHolder, actionState ->
              handleDragSelected(viewHolder, actionState) { draggedListId = it }
          },
          onClearViewCallback = { recyclerView, viewHolder ->
              draggedListId = null
              handleDragClear(recyclerView, viewHolder)
          }
      )
      
      itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(swipeHelper)
      itemTouchHelper?.attachToRecyclerView(binding.recyclerTasks)
  }

  private fun handleTaskSwipe(position: Int, direction: Int) {
      val adapter = binding.recyclerTasks.adapter
      val task = when (adapter) {
          is app.polar.ui.adapter.HomeTaskAdapter -> {
              (adapter.currentList.getOrNull(position) as? HomeItem.TaskItem)?.task
          }
          is TaskAdapter -> {
              (adapter.currentList.getOrNull(position) as? TaskListItem.Item)?.task
          }
          else -> null
      }

      task ?: return

      if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
          viewModel.moveToTrash(task)
          com.google.android.material.snackbar.Snackbar.make(
              binding.root,
              getString(R.string.task_moved_trash),
              com.google.android.material.snackbar.Snackbar.LENGTH_LONG
          ).setAction(getString(R.string.undo)) { viewModel.restoreFromTrash(task) }.show()
      } else {
          viewModel.toggleTaskCompletion(task)
      }
  }

  private fun handleItemMove(fromPosition: Int, toPosition: Int): Boolean {
      val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter ?: return false
      val list = adapter.currentList.toMutableList()
      
      if (fromPosition !in list.indices || toPosition !in list.indices) return false
      
      val fromItem = list[fromPosition]
      val toItem = list[toPosition]
      
      // Solo permitimos reordenar grupos completos arrastrando sus cabeceras.
      if (fromItem !is HomeItem.Header || toItem !is HomeItem.Header) return false
      
      val fromRange = getGroupRange(list, fromPosition)
      val toRange = getGroupRange(list, toPosition)
      
      // Evitar solapamiento entre el grupo origen y destino.
      if (fromRange.first <= toRange.last && toRange.first <= fromRange.last) return false
      
      val groupToMove = list.subList(fromRange.first, fromRange.last + 1).toList()
      list.subList(fromRange.first, fromRange.last + 1).clear()
      
      val insertIndex = if (fromRange.first < toRange.first) {
          toRange.last - groupToMove.size + 1
      } else {
          toRange.first
      }
      
      list.addAll(insertIndex, groupToMove)
      
      val animator = binding.recyclerTasks.itemAnimator
      binding.recyclerTasks.itemAnimator = null
      adapter.submitList(list) {
          binding.recyclerTasks.itemAnimator = animator
      }
      return true
  }

  private fun getGroupRange(list: List<HomeItem>, headerPosition: Int): IntRange {
      var end = headerPosition
      while (end + 1 < list.size &&
          (list[end + 1] is HomeItem.TaskItem || list[end + 1] is HomeItem.CompletedHeader)) {
          end++
      }
      return headerPosition..end
  }

  private fun handleDragSelected(
      viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder?,
      actionState: Int,
      setDraggedListId: (Long?) -> Unit
  ) {
      if (actionState != androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) return
      
      val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter ?: return
      
      if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) {
          val position = viewHolder.bindingAdapterPosition
          if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
          
          val items = adapter.currentList
          val headerItem = items[position] as? HomeItem.Header
          setDraggedListId(headerItem?.listId)
          
          headerItem?.listId?.let { draggedId ->
              for (i in items.indices) {
                  val belongsToDraggedGroup = when (val item = items[i]) {
                      is HomeItem.Header -> item.listId == draggedId
                      is HomeItem.CompletedHeader -> false // los completados no se mueven con el grupo
                      is HomeItem.TaskItem -> findHeaderListId(items, i) == draggedId
                  }
                  if (belongsToDraggedGroup) {
                      binding.recyclerTasks.findViewHolderForAdapterPosition(i)?.itemView?.alpha = 0.5f
                  }
              }
          }
      } else {
          viewHolder?.itemView?.alpha = 0.5f
      }
  }

  private fun handleDragClear(
      recyclerView: androidx.recyclerview.widget.RecyclerView,
      viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder
  ) {
      if (viewHolder is app.polar.ui.adapter.HomeTaskAdapter.HeaderViewHolder) {
          for (i in 0 until recyclerView.childCount) {
              recyclerView.getChildAt(i)?.animate()
                  ?.alpha(1.0f)
                  ?.translationY(0f)
                  ?.setDuration(150)
                  ?.start()
          }
          
          val adapter = binding.recyclerTasks.adapter as? app.polar.ui.adapter.HomeTaskAdapter ?: return
          val items = adapter.currentList
          val headers = items.filterIsInstance<HomeItem.Header>()
          val orderMap = headers.mapIndexed { index, header -> header.listId to index }.toMap()
          
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

  private fun findHeaderListId(items: List<HomeItem>, position: Int): Long? {
      for (j in position downTo 0) {
          if (items[j] is HomeItem.Header) {
              return (items[j] as HomeItem.Header).listId
          }
      }
      return null
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
                      latestTaskItems = tasks
                      if (viewModel.selectedListId.value != -1L) {
                          submitTaskListWithCompletedSection()
                      }
                  }
              }
              
              launch {
                  viewModel.completedTasksExpanded.collect {
                      if (viewModel.selectedListId.value != -1L) {
                          submitTaskListWithCompletedSection()
                      } else {
                          submitHomeListWithCompletedSection()
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

  private fun submitTaskListWithCompletedSection() {
      val expanded = viewModel.completedTasksExpanded.value
      val items = buildTaskListWithCompletedSection(latestTaskItems, expanded)
      updateEmptyState(items.isEmpty())
      taskAdapter.submitList(items)
  }

  private fun buildTaskListWithCompletedSection(
      items: List<TaskListItem>,
      expanded: Boolean
  ): List<TaskListItem> {
      val result = mutableListOf<TaskListItem>()
      val pending = items.filterIsInstance<TaskListItem.Item>().filter { !it.task.completed }
      val completed = items.filterIsInstance<TaskListItem.Item>().filter { it.task.completed }
      
      result.addAll(pending)
      if (completed.isNotEmpty()) {
          result.add(TaskListItem.CompletedHeader(completed.size, expanded))
          if (expanded) {
              result.addAll(completed)
          }
      }
      return result
  }

  private fun updateHomeUI(groups: List<app.polar.data.model.TaskGroup>) {
      // Only update the home RecyclerView when actually in home mode
      if (viewModel.selectedListId.value != -1L) return

      latestHomeGroups = groups
      submitHomeListWithCompletedSection()
  }

  private fun submitHomeListWithCompletedSection() {
      val items = buildHomeItems(latestHomeGroups)
      val hasTasks = items.any { it is HomeItem.TaskItem }
      updateEmptyState(!hasTasks)
      homeTaskAdapter.submitList(items)
  }

  private fun buildHomeItems(
      groups: List<app.polar.data.model.TaskGroup>
  ): List<HomeItem> {
      val items = mutableListOf<HomeItem>()
      groups.forEach { group ->
          val pending = group.tasks.filter { !it.completed }
          if (pending.isNotEmpty()) {
              items.add(HomeItem.Header(group.listId, group.title))
              items.addAll(pending.map { HomeItem.TaskItem(it) })
          }
      }
      return items
  }
  
  private fun updateEmptyState(isEmpty: Boolean) {
       binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
       binding.recyclerTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
      binding.recyclerTasks.itemAnimator = TaskItemAnimator()
  }

  private fun showTaskPopupMenu(task: Task, anchorView: View? = null) {
    val view = anchorView ?: run {
        val position = taskAdapter.currentList.indexOfFirst {
            it is TaskListItem.Item && it.task.id == task.id
        }
        if (position != -1) {
            binding.recyclerTasks.findViewHolderForAdapterPosition(position)?.itemView
        } else {
            val homePosition = homeTaskAdapter.currentList.indexOfFirst {
                it is HomeItem.TaskItem && it.task.id == task.id
            }
            if (homePosition != -1) {
                binding.recyclerTasks.findViewHolderForAdapterPosition(homePosition)?.itemView
            } else null
        }
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
  
  private fun resolveColorAttr(attr: Int): Int {
      val typedValue = android.util.TypedValue()
      requireContext().theme.resolveAttribute(attr, typedValue, true)
      return typedValue.data
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
