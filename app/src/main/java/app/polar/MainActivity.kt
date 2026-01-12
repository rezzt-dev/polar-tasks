package app.polar

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.databinding.ActivityMainBinding
import app.polar.ui.adapter.TaskListAdapter
import app.polar.ui.dialog.TaskDialog
import app.polar.ui.dialog.TaskListDialog
import app.polar.ui.fragment.TasksFragment
import app.polar.ui.viewmodel.TaskListViewModel
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.util.ThemeManager

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var themeManager: ThemeManager
  
  private val taskListViewModel: TaskListViewModel by viewModels()
  private val taskViewModel: TaskViewModel by viewModels()
  
  private lateinit var taskListAdapter: TaskListAdapter
  private var currentListId: Long? = null
  
  override fun onCreate(savedInstanceState: Bundle?) {
    // Apply theme before super.onCreate
    themeManager = ThemeManager(this)
    themeManager.applyTheme(themeManager.loadTheme())
    
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    setupToolbar()
    setupDrawer()
    setupFab()
    observeTaskLists()
    
    // Load initial fragment
    if (savedInstanceState == null) {
      if (intent.getBooleanExtra("NAVIGATE_TO_REMINDERS", false)) {
          openReminders()
      } else {
          supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TasksFragment())
            .commit()
      }
    }
  }
  
  override fun onNewIntent(intent: android.content.Intent) {
      super.onNewIntent(intent)
      if (intent.getBooleanExtra("NAVIGATE_TO_REMINDERS", false)) {
          openReminders()
      }
  }

  override fun onResume() {
    super.onResume()
    // Asegurarse de que el SearchView esté colapsado al volver
    invalidateOptionsMenu()
  }

  private fun setupToolbar() {
    setSupportActionBar(binding.toolbar)
    
    // Configuración para Edge-to-Edge:
    // La barra de estado se maneja vía fitsSystemWindows en el layout (activity_main.xml)
    // No necesitamos establecer colores manualmente aquí si el layout está bien configurado.

    binding.toolbar.setNavigationOnClickListener {
      binding.drawerLayout.openDrawer(GravityCompat.START)
    }
  }
  
  private fun setupDrawer() {
    taskListAdapter = TaskListAdapter(
        onItemClick = { taskList ->
            currentListId = taskList.id
            taskListViewModel.selectList(taskList.id)
            taskViewModel.loadTasksForList(taskList.id)
            binding.toolbar.title = taskList.title
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, TasksFragment())
                .commit()
                
            binding.fabAddTask.show()
            binding.drawerLayout.close()
        },
        onItemLongClick = { taskList ->
            showTaskListPopupMenu(taskList)
            true
        }
    )
    
    val rvTaskLists = binding.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTaskLists)
    rvTaskLists.apply {
      layoutManager = LinearLayoutManager(this@MainActivity)
      adapter = taskListAdapter
    }

    val dragDropHelper = app.polar.util.DragDropHelper(
        onItemMove = { from, to -> taskListAdapter.onItemMove(from, to) },
        onMoveFinished = {
            val taskLists = taskListAdapter.currentList.mapIndexed { index, taskList ->
                taskList.copy(orderIndex = index)
            }
            taskViewModel.updateTaskListsOrder(taskLists)
        }
    )
    androidx.recyclerview.widget.ItemTouchHelper(dragDropHelper).attachToRecyclerView(rvTaskLists)
    
    val btnCreateList = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreateList)
    btnCreateList.setOnClickListener {
      showCreateListDialog()
    }
    
    val btnHome = binding.root.findViewById<android.widget.LinearLayout>(R.id.btnHome)
    btnHome.setOnClickListener {
        currentListId = -1L
        taskViewModel.loadAllTasks()
        binding.toolbar.title = "Home"
        binding.fabAddTask.hide()
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TasksFragment())
            .commit()
            
        binding.drawerLayout.close()
    }
    
    val btnCalendar = binding.root.findViewById<android.widget.LinearLayout>(R.id.btnCalendar)
    btnCalendar.setOnClickListener {
        currentListId = null
        binding.toolbar.title = "Calendar"
        binding.fabAddTask.hide()
        
        supportFragmentManager.beginTransaction()
          .replace(R.id.fragmentContainer, app.polar.ui.fragment.CalendarFragment())
          .commit()
          
        binding.drawerLayout.close()
    }

    val btnReminders = binding.root.findViewById<android.widget.LinearLayout>(R.id.btnReminders)
    btnReminders.setOnClickListener {
        openReminders()
        binding.drawerLayout.close()
    }
  }
  
  private fun openReminders() {
      currentListId = -2L // Special ID for Reminders
      binding.toolbar.title = "Reminders"
      binding.fabAddTask.show()
      
      supportFragmentManager.beginTransaction()
          .replace(R.id.fragmentContainer, app.polar.ui.fragment.RemindersFragment())
          .commit()
  }
  
  private fun setupFab() {
    binding.fabAddTask.setOnClickListener {
      if (currentListId == -2L) {
          app.polar.ui.dialog.ReminderDialog(
              onSave = { title, desc, time ->
                   // ViewModel call logic is inside Fragment usually if we use FragmentManager,
                   // but here the FAB is in Activity.
                   // We need to communicate with RemindersViewModel.
                   // Since ViewModel is scoped to Activity (by viewModels()), we can use it here.
                   val remindersViewModel: app.polar.ui.viewmodel.RemindersViewModel by viewModels()
                   remindersViewModel.insert(title, desc, time)
              }
          ).show(supportFragmentManager, "CreateReminderDialog")
      } else {
          currentListId?.let { listId ->
            showCreateTaskDialog(listId)
          }
      }
    }
  }
  
  private fun observeTaskLists() {
    taskListViewModel.allTaskLists.observe(this) { lists ->
      taskListAdapter.submitList(lists)
      
      // Select first list if none selected
      if (currentListId == null) {
          // Default to Home/All Tasks
          taskViewModel.loadAllTasks()
          currentListId = -1L
          binding.toolbar.title = "Home"
          binding.fabAddTask.hide()
      }
    }
  }
  
  private fun showCreateListDialog() {
    TaskListDialog(
      taskList = null,
      onSave = { title, icon ->
        taskListViewModel.insertTaskList(title, icon)
      }
    ).show(supportFragmentManager, "CreateListDialog")
  }
  
  private fun showEditListDialog(taskList: app.polar.data.entity.TaskList) {
    TaskListDialog(
      taskList = taskList,
      onSave = { title, icon ->
        taskListViewModel.updateTaskList(taskList.copy(title = title, icon = icon))
        if (currentListId == taskList.id) {
          binding.toolbar.title = title
        }
      }
    ).show(supportFragmentManager, "EditListDialog")
  }
  
  private fun showTaskListPopupMenu(taskList: app.polar.data.entity.TaskList) {
    // Find the view for this task list
    val view = binding.rvTaskLists.findViewHolderForAdapterPosition(
      taskListAdapter.currentList.indexOf(taskList)
    )?.itemView ?: return
    
    PopupMenu(this, view).apply {
      menuInflater.inflate(R.menu.menu_task_list, menu)
      setOnMenuItemClickListener { item ->
        when (item.itemId) {
          R.id.action_edit -> {
            showEditListDialog(taskList)
            true
          }
          R.id.action_delete -> {
            taskListViewModel.deleteTaskList(taskList)
            if (currentListId == taskList.id) {
              currentListId = null
              binding.toolbar.title = getString(R.string.app_name)
            }
            true
          }
          else -> false
        }
      }
      show()
    }
  }
  
  private fun showCreateTaskDialog(listId: Long) {
    TaskDialog(
      task = null,
      existingSubtasks = emptyList(),
      onSave = { title, description, tags, subtaskTitles, dueDate ->
        taskViewModel.insertTask(listId, title, description, tags, subtaskTitles, dueDate)
      }
    ).show(supportFragmentManager, "CreateTaskDialog")
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)

    // Setup search
    val searchItem = menu.findItem(R.id.action_search)
    val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
    searchView.queryHint = getString(R.string.search_hint)

    searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
      override fun onQueryTextSubmit(query: String?): Boolean {
        query?.let {
          performSearch(it)
          // IMPORTANTE: Cerrar el SearchView después de buscar
          searchView.clearFocus()
          searchItem.collapseActionView()
        }
        return true
      }

      override fun onQueryTextChange(newText: String?): Boolean {
        // Optional: implement real-time search
        return true
      }
    })

    return true
  }

  private fun performSearch(query: String) {
    if (query.isNotEmpty()) {
      val intent = android.content.Intent(this, app.polar.ui.activity.SearchResultsActivity::class.java)
      intent.putExtra(app.polar.ui.activity.SearchResultsActivity.EXTRA_SEARCH_QUERY, query)
      startActivity(intent)
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_theme -> {
        themeManager.toggleTheme()
        recreate() // Recreate activity to apply new theme resources
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }
  
  override fun onBackPressed() {
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
      binding.drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      super.onBackPressed()
    }
  }
}