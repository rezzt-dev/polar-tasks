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
import androidx.appcompat.R as AppCompatR
import app.polar.util.TaskSwipeHelper
import com.google.android.material.R as MaterialR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import android.widget.PopupMenu
import androidx.core.graphics.ColorUtils

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
        object Tags : NavigationEvent()
        object Reminders : NavigationEvent()
        object Eisenhower : NavigationEvent()
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
        val mainContent = binding.root.findViewById<android.view.View>(R.id.mainContent)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // On Android 12+ the area behind the drawer becomes a see-through blur of the
            // activity content instead of the usual dim scrim.
            binding.drawerLayout.setScrimColor(android.graphics.Color.TRANSPARENT)
            binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    if (slideOffset > 0f) {
                        val radius = (slideOffset * MAX_BLUR_RADIUS).coerceAtLeast(MIN_BLUR_RADIUS)
                        mainContent.setRenderEffect(
                            android.graphics.RenderEffect.createBlurEffect(
                                radius,
                                radius,
                                android.graphics.Shader.TileMode.CLAMP
                            )
                        )
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
        } else {
            // Fallback for older versions: a subtle theme-aware dim.
            val scrimBase = resolveThemeColor(MaterialR.attr.colorOnSurface)
            binding.drawerLayout.setScrimColor(ColorUtils.setAlphaComponent(scrimBase, FALLBACK_SCRIM_ALPHA))
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

        val swipeHelper = TaskSwipeHelper(
            rightSwipeConfig = TaskSwipeHelper.SwipeConfig(
                backgroundColorAttr = AppCompatR.attr.colorPrimary,
                iconRes = R.drawable.ic_edit,
                iconTintAttr = MaterialR.attr.colorOnPrimary
            ),
            leftSwipeConfig = TaskSwipeHelper.SwipeConfig(
                backgroundColorAttr = R.attr.colorError,
                iconRes = R.drawable.ic_trash
            ),
            getDragFlagsForHolder = {
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN
            },
            getSwipeFlagsForHolder = {
                androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
            },
            onMoveCallback = { from, to ->
                taskListAdapter.onItemMove(from, to)
                true
            },
            onSwipedRight = { position ->
                val taskList = taskListAdapter.currentList.getOrNull(position)
                if (taskList != null) {
                    onNavigate(NavigationEvent.EditList(taskList))
                    // Postpone the UI update to avoid IllegalStateException while RecyclerView
                    // is still processing the swipe animation.
                    rvTaskLists.post { taskListAdapter.notifyItemChanged(position) }
                }
            },
            onSwipedLeft = { position ->
                val taskList = taskListAdapter.currentList.getOrNull(position)
                if (taskList != null) {
                    showDeleteListConfirmation(taskList, position)
                }
            },
            onClearViewCallback = { _, _ ->
                val taskLists = taskListAdapter.currentList.mapIndexed { index, list ->
                    list.copy(orderIndex = index)
                }
                taskViewModel.updateTaskListsOrder(taskLists)
            },
            swipeThreshold = 0.25f
        )
        androidx.recyclerview.widget.ItemTouchHelper(swipeHelper).attachToRecyclerView(rvTaskLists)
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

        binding.root.findViewById<View>(R.id.btnTags).setOnClickListener {
            onNavigate(NavigationEvent.Tags)
            closeDrawer()
        }

        binding.root.findViewById<View>(R.id.btnReminders).setOnClickListener {
            onNavigate(NavigationEvent.Reminders)
            closeDrawer()
        }

        binding.root.findViewById<View>(R.id.btnEisenhower).setOnClickListener {
            onNavigate(NavigationEvent.Eisenhower)
            closeDrawer()
        }

        binding.root.findViewById<View>(R.id.btnTrash).setOnClickListener {
            onNavigate(NavigationEvent.Trash)
            closeDrawer()
        }
    }

    private fun showDeleteListConfirmation(taskList: TaskList, position: Int) {
        val rvTaskLists = binding.root.findViewById<RecyclerView>(R.id.rvTaskLists)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_list)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                taskListViewModel.deleteTaskList(taskList)
                Snackbar.make(binding.drawerLayout, activity.getString(R.string.list_deleted), Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                rvTaskLists.post { taskListAdapter.notifyItemChanged(position) }
            }
            .setOnCancelListener {
                rvTaskLists.post { taskListAdapter.notifyItemChanged(position) }
            }
            .show()
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

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    companion object {
        private const val MAX_BLUR_RADIUS = 24f
        private const val MIN_BLUR_RADIUS = 1f
        private const val FALLBACK_SCRIM_ALPHA = 100
    }
}
