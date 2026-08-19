package app.polar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

import app.polar.ui.activity.BaseActivity

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {
  private lateinit var binding: ActivityMainBinding
  // themeManager is already in BaseActivity
  
  private val taskListViewModel: TaskListViewModel by viewModels()
  private val taskViewModel: TaskViewModel by viewModels()
  private val remindersViewModel: app.polar.ui.viewmodel.RemindersViewModel by viewModels()
  
  private lateinit var drawerManager: app.polar.ui.manager.DrawerManager
  private var currentListId: Long? = null
  
  private val requestNotificationPermissionLauncher = registerForActivityResult(
      ActivityResultContracts.RequestPermission()
  ) { _ ->
      // App continues regardless of permission result
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    // BaseActivity handles theme init
    
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    setupToolbar()
    setupDrawerManager()
    setupFab()
    binding.fabAddTask.hide()
    // observeTaskLists() moved to DrawerManager
    
    // Load initial fragment
    if (savedInstanceState == null) {
      if (intent.getBooleanExtra("NAVIGATE_TO_REMINDERS", false)) {
          openReminders()
      } else {
          currentListId = -1L
          taskViewModel.loadAllTasks()
          binding.toolbar.title = getString(R.string.home)
          supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TasksFragment())
            .commit()
      }
    }
    
    onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (drawerManager.isDrawerOpen()) {
                drawerManager.closeDrawer()
                return
            }

            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (currentFragment is app.polar.ui.fragment.StatsFragment ||
                currentFragment is app.polar.ui.fragment.SettingsFragment) {
                navigateToHome()
                return
            }

            if (isEnabled) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    })
    
    checkFirstRun()
    requestNotificationPermissionIfNeeded()
  }
    private fun checkFirstRun() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = sharedPrefs.getBoolean("is_first_run", true)
        
        if (isFirstRun) {
            startActivity(android.content.Intent(this, app.polar.ui.activity.TutorialActivity::class.java))
            finish()
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
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
    // Ensure SearchView is collapsed on return
    invalidateOptionsMenu()
  }

  private fun setupToolbar() {
    setSupportActionBar(binding.toolbar)
    
    // Edge-to-Edge Configuration:
    // Status bar handled via fitsSystemWindows in layout (activity_main.xml)
    // No need to set colors manually here if layout is configured correctly.

    binding.toolbar.setNavigationOnClickListener {
      drawerManager.openDrawer()
    }
  }
  
  private fun setupDrawerManager() {
      drawerManager = app.polar.ui.manager.DrawerManager(
          this, 
          binding, 
          taskListViewModel, 
          taskViewModel, 
          remindersViewModel
      ) { event -> handleNavigation(event) }
      drawerManager.setup()
  }

  private fun handleNavigation(event: app.polar.ui.manager.DrawerManager.NavigationEvent) {
      when (event) {
          is app.polar.ui.manager.DrawerManager.NavigationEvent.Home -> {
                currentListId = -1L
                taskViewModel.loadAllTasks()
                binding.toolbar.title = getString(R.string.home)
                binding.fabAddTask.hide()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, TasksFragment())
                    .commit()
          }
          is app.polar.ui.manager.DrawerManager.NavigationEvent.Calendar -> {
                currentListId = null
                binding.toolbar.title = getString(R.string.calendar)
                binding.fabAddTask.hide()
                supportFragmentManager.beginTransaction()
                  .replace(R.id.fragmentContainer, app.polar.ui.fragment.CalendarFragment())
                  .commit()
          }
          is app.polar.ui.manager.DrawerManager.NavigationEvent.Tags -> {
                currentListId = null
                binding.toolbar.title = getString(R.string.all_tags)
                binding.fabAddTask.hide()
                supportFragmentManager.beginTransaction()
                  .replace(R.id.fragmentContainer, app.polar.ui.fragment.TagsFragment())
                  .commit()
          }
          is app.polar.ui.manager.DrawerManager.NavigationEvent.Reminders -> {
                openReminders()
          }
          is app.polar.ui.manager.DrawerManager.NavigationEvent.Eisenhower -> {
                currentListId = null
                binding.toolbar.title = getString(R.string.eisenhower_matrix)
                binding.fabAddTask.hide()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, app.polar.ui.fragment.EisenhowerFragment())
                    .commit()
          }
          is app.polar.ui.manager.DrawerManager.NavigationEvent.Trash -> {
                currentListId = -3L
                binding.toolbar.title = getString(R.string.trash)
                binding.fabAddTask.hide()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, app.polar.ui.fragment.TrashFragment())
                    .commit()
          }
          is app.polar.ui.manager.DrawerManager.NavigationEvent.TaskListSelected -> {
                val taskList = event.list
                currentListId = taskList.id
                taskListViewModel.selectList(taskList.id)
                taskViewModel.loadTasksForList(taskList.id)
                binding.toolbar.title = taskList.title
                
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, TasksFragment())
                    .commit()
                binding.fabAddTask.show()
          }
           is app.polar.ui.manager.DrawerManager.NavigationEvent.CreateList -> {
                showCreateListDialog()
           }
           is app.polar.ui.manager.DrawerManager.NavigationEvent.EditList -> {
                showEditListDialog(event.list)
           }
      }
  }

  
  private fun openReminders() {
      currentListId = -2L // Special ID for Reminders
      binding.toolbar.title = getString(R.string.reminders)
      binding.fabAddTask.show()
      
      supportFragmentManager.beginTransaction()
          .replace(R.id.fragmentContainer, app.polar.ui.fragment.RemindersFragment())
          .commit()
  }
  
  private fun navigateToHome() {
      currentListId = -1L
      taskViewModel.loadAllTasks()
      binding.toolbar.title = getString(R.string.home)
      binding.fabAddTask.hide()
      supportFragmentManager.beginTransaction()
          .replace(R.id.fragmentContainer, TasksFragment())
          .commit()
  }
  
  private fun setupFab() {
    binding.fabAddTask.setOnClickListener {
      if (currentListId == -2L) {
          app.polar.ui.dialog.ReminderDialog(
              onSaveWithLocation = { title, desc, time, lat, lng, radius, locName ->
                   val remindersViewModel: app.polar.ui.viewmodel.RemindersViewModel by viewModels()
                   remindersViewModel.insert(title, desc, time, lat, lng, radius, locName)
              }
          ).show(supportFragmentManager, "CreateReminderDialog")
      } else {
          currentListId?.let { listId ->
            showCreateTaskDialog(listId)
          }
      }
    }
  }
  

  
  private fun showCreateListDialog() {
    TaskListDialog(
      taskList = null,
      onSaveWithColor = { title, icon, isChain, color ->
        taskListViewModel.insertTaskList(title, icon, isChain, color)
      }
    ).show(supportFragmentManager, "CreateListDialog")
  }
  
  private fun showEditListDialog(taskList: app.polar.data.entity.TaskList) {
    TaskListDialog(
      taskList = taskList,
      onSaveWithColor = { title, icon, isChain, color ->
        taskListViewModel.updateTaskList(taskList.copy(title = title, icon = icon, isDependencyChain = isChain, color = color))
        if (currentListId == taskList.id) {
          binding.toolbar.title = title
        }
      }
    ).show(supportFragmentManager, "EditListDialog")
  }
  

  
  private fun showCreateTaskDialog(listId: Long) {
    TaskDialog(
      task = null,
      existingSubtasks = emptyList(),
      onSave = { title, description, tags, subtaskList, dueDate, recurrence, priority, timeEstimate ->
        taskViewModel.insertTask(listId, title, description, tags, subtaskList, dueDate, recurrence, priority, timeEstimate)
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
          // IMPORTANT: Close SearchView after search
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
      R.id.action_stats -> {
        binding.fabAddTask.hide()
        currentListId = null
        binding.toolbar.title = getString(R.string.statistics)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, app.polar.ui.fragment.StatsFragment())
            .commit()
        true
      }
      R.id.action_settings -> {
        binding.fabAddTask.hide()
        currentListId = null
        binding.toolbar.title = getString(R.string.settings)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, app.polar.ui.fragment.SettingsFragment())
            .commit()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }
  
}