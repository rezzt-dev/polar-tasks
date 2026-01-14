package app.polar.ui.manager

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.TaskList
import app.polar.databinding.ActivityMainBinding
import app.polar.ui.adapter.TaskListAdapter
import app.polar.ui.viewmodel.RemindersViewModel
import app.polar.ui.viewmodel.TaskListViewModel
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.util.DragDropHelper
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import android.widget.PopupMenu

class DrawerManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val taskListViewModel: TaskListViewModel,
    private val taskViewModel: TaskViewModel,
    private val remindersViewModel: RemindersViewModel,
    private val onNavigate: (NavigationEvent) -> Unit
) {

    sealed class NavigationEvent {
        object Home : NavigationEvent()
        object Calendar : NavigationEvent()
        object Reminders : NavigationEvent()
        object Trash : NavigationEvent()
        data class TaskListSelected(val list: TaskList) : NavigationEvent()
        object CreateList : NavigationEvent()
        data class EditList(val list: TaskList) : NavigationEvent()
    }

    private lateinit var taskListAdapter: TaskListAdapter

    fun setup() {
        setupDrawerBlur()
        setupRecyclerView()
        setupStaticItems()
        observeData()
    }

    private fun setupDrawerBlur() {
        binding.drawerLayout.setScrimColor(android.graphics.Color.parseColor("#99000000"))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val mainContent = binding.root.findViewById<android.view.View>(R.id.mainContent)
            binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    if (slideOffset > 0) {
                        val radius = slideOffset * 20f + 1f
                        mainContent.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP))
                    } else {
                        mainContent.setRenderEffect(null)
                    }
                }
                override fun onDrawerOpened(drawerView: View) {
                    binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_OPEN)
                }
                override fun onDrawerClosed(drawerView: View) {
                    mainContent.setRenderEffect(null)
                    binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                override fun onDrawerStateChanged(newState: Int) {}
            })
        }
    }

    private fun setupRecyclerView() {
        taskListAdapter = TaskListAdapter(
            onItemClick = { taskList ->
                onNavigate(NavigationEvent.TaskListSelected(taskList))
                closeDrawer()
            },
            onItemLongClick = { taskList ->
                showTaskListPopupMenu(taskList)
                true
            }
        )

        val rvTaskLists = binding.root.findViewById<RecyclerView>(R.id.rvTaskLists)
        rvTaskLists.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = taskListAdapter
        }

        val dragDropHelper = DragDropHelper(
            onItemMove = { from, to -> taskListAdapter.onItemMove(from, to) },
            onMoveFinished = {
                val taskLists = taskListAdapter.currentList.mapIndexed { index, taskList ->
                    taskList.copy(orderIndex = index)
                }
                taskViewModel.updateTaskListsOrder(taskLists)
            },
            onSwiped = { position, direction ->
                val taskList = taskListAdapter.currentList[position]
                if (direction == ItemTouchHelper.START) {
                    taskListViewModel.deleteTaskList(taskList)
                    Snackbar.make(binding.drawerLayout, activity.getString(R.string.list_deleted), Snackbar.LENGTH_SHORT).show()
                    // If current list was deleted, navigation handling is needed in MainActivity via callback usually?
                    // Currently we don't know the current list ID here easily without tracking it.
                    // For now, simple delete. MainActivity observes onDelete logic if implemented, or we rely on ViewModel logic.
                } else {
                    onNavigate(NavigationEvent.EditList(taskList))
                    taskListAdapter.notifyItemChanged(position)
                }
            }
        )
        ItemTouchHelper(dragDropHelper).attachToRecyclerView(rvTaskLists)
    }

    private fun setupStaticItems() {
        binding.root.findViewById<View>(R.id.btnCreateList).setOnClickListener {
            onNavigate(NavigationEvent.CreateList)
        }

        binding.root.findViewById<View>(R.id.btnHome).setOnClickListener {
            onNavigate(NavigationEvent.Home)
            closeDrawer()
        }

        binding.root.findViewById<View>(R.id.btnCalendar).setOnClickListener {
            onNavigate(NavigationEvent.Calendar)
            closeDrawer()
        }

        binding.root.findViewById<View>(R.id.btnReminders).setOnClickListener {
            onNavigate(NavigationEvent.Reminders)
            closeDrawer()
        }

        binding.root.findViewById<View>(R.id.btnTrash).setOnClickListener {
            onNavigate(NavigationEvent.Trash)
            closeDrawer()
        }
    }

    private fun observeData() {
        taskListViewModel.allTaskLists.observe(activity) { lists ->
            taskListAdapter.submitList(lists)
        }

        val tvTrashCount = binding.root.findViewById<android.widget.TextView>(R.id.tvTrashCount)
        val updateTrashCount = {
            val taskCount = taskViewModel.getDeletedTasks().value?.size ?: 0
            val reminderCount = remindersViewModel.getDeletedReminders().value?.size ?: 0
            val total = taskCount + reminderCount
            tvTrashCount.text = if (total > 0) activity.getString(R.string.trash_with_count, total) else activity.getString(R.string.trash)
        }

        taskViewModel.getDeletedTasks().observe(activity) { updateTrashCount() }
        remindersViewModel.getDeletedReminders().observe(activity) { updateTrashCount() }
    }

    private fun showTaskListPopupMenu(taskList: TaskList) {
        val view = binding.root.findViewById<RecyclerView>(R.id.rvTaskLists).findViewHolderForAdapterPosition(
            taskListAdapter.currentList.indexOf(taskList)
        )?.itemView ?: return

        PopupMenu(activity, view).apply {
            menuInflater.inflate(R.menu.menu_task_list, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        onNavigate(NavigationEvent.EditList(taskList))
                        true
                    }
                    R.id.action_delete -> {
                        taskListViewModel.deleteTaskList(taskList)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }
    
    fun isDrawerOpen(): Boolean = binding.drawerLayout.isDrawerOpen(GravityCompat.START)
}
