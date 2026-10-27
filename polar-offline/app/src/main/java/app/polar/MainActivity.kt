package app.polar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
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
import app.polar.ui.manager.DrawerManager
import app.polar.ui.viewmodel.TaskListViewModel
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.util.ThemeManager

import app.polar.ui.activity.BaseActivity

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {
  private lateinit var binding: ActivityMainBinding
  val snackbarAnchor: android.view.View get() = binding.fabAddTask
  // themeManager is already in BaseActivity
  
  private val taskListViewModel: TaskListViewModel by viewModels()
  private val taskViewModel: TaskViewModel by viewModels()
  private val remindersViewModel: app.polar.ui.viewmodel.RemindersViewModel by viewModels()
  
  private lateinit var drawerManager: DrawerManager
  private var currentListId: Long? = null
  private var activeDestination = DrawerManager.Destination.HOME
  
  private val requestNotificationPermissionLauncher = registerForActivityResult(
      ActivityResultContracts.RequestPermission()
  ) { _ ->
      // App continues regardless of permission result
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    // BaseActivity handles theme init
    
    super.onCreate(savedInstanceState)
    // En el primer arranque se muestra el tutorial; no tiene sentido montar la pantalla principal.
    if (checkFirstRun()) return

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    
    setupToolbar()
    setupDrawerManager()
    setupFab()
    binding.fabAddTask.hide()
    // observeTaskLists() moved to DrawerManager
    
    // Load initial fragment
    if (savedInstanceState != null) {
      restoreNavigationState(savedInstanceState)
    } else {
      if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_REMINDERS, false)) {
          openReminders()
      } else {
          currentListId = -1L
          taskViewModel.loadAllTasks()
          showScreen(TasksFragment(), getString(R.string.home), DrawerManager.Destination.HOME, animate = false)
      }
    }
    
    onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (drawerManager.isDrawerOpen()) {
                drawerManager.closeDrawer()
                return
            }

            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (currentFragment is app.polar.ui.fragment.SettingsFragment && currentFragment.navigateBack()) {
                return
            }
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
    
    requestNotificationPermissionIfNeeded()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (!::binding.isInitialized) return
    currentListId?.let { outState.putLong(STATE_CURRENT_LIST_ID, it) }
    outState.putCharSequence(STATE_TOOLBAR_TITLE, binding.toolbar.title)
    outState.putString(STATE_DESTINATION, activeDestination.name)
  }

  // El FragmentManager ya restaura el fragmento visible; aquí se recupera lo que vive en la
  // activity (lista actual, título y botón de añadir), que antes se perdía al recrearla (rotación,
  // cambio de tema o idioma, restaurar una copia).
  private fun restoreNavigationState(savedInstanceState: Bundle) {
    currentListId = if (savedInstanceState.containsKey(STATE_CURRENT_LIST_ID)) {
      savedInstanceState.getLong(STATE_CURRENT_LIST_ID)
    } else {
      null
    }
    savedInstanceState.getCharSequence(STATE_TOOLBAR_TITLE)?.let { binding.toolbar.title = it }
    activeDestination = savedInstanceState.getString(STATE_DESTINATION)
      ?.let { runCatching { DrawerManager.Destination.valueOf(it) }.getOrNull() }
      ?: DrawerManager.Destination.HOME
    drawerManager.setActiveDestination(activeDestination, currentListId)

    val listId = currentListId
    // Tras la muerte del proceso el ViewModel vuelve a empezar en "inicio": recargar la lista.
    if (listId != null && listId > 0 && taskViewModel.selectedListId.value != listId) {
      taskListViewModel.selectList(listId)
      taskViewModel.loadTasksForList(listId)
    }
    if (listId != null && (listId > 0 || listId == REMINDERS_LIST_ID)) {
      binding.fabAddTask.show()
    }
  }

    private fun checkFirstRun(): Boolean {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = sharedPrefs.getBoolean("is_first_run", true)
        
        if (isFirstRun) {
            startActivity(android.content.Intent(this, app.polar.ui.activity.TutorialActivity::class.java))
            finish()
        }
        return isFirstRun
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
      if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_REMINDERS, false)) {
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
      drawerManager = DrawerManager(
          this, 
          binding, 
          taskListViewModel, 
          taskViewModel, 
          remindersViewModel
      ) { event -> handleNavigation(event) }
      drawerManager.setup()
  }

  /**
   * Muestra una sección principal con la transición "fade through" (fundido con un leve escalado)
   * y sincroniza el título de la barra y la sección marcada en el cajón.
   */
  private fun showScreen(
      fragment: androidx.fragment.app.Fragment,
      title: CharSequence,
      destination: DrawerManager.Destination,
      animate: Boolean = true
  ) {
      binding.toolbar.title = title
      activeDestination = destination
      drawerManager.setActiveDestination(destination, currentListId)
      supportFragmentManager.beginTransaction().apply {
          if (animate) {
              setCustomAnimations(R.anim.polar_fade_through_enter, R.anim.polar_fade_through_exit)
          }
          setReorderingAllowed(true)
          replace(R.id.fragmentContainer, fragment)
      }.commit()
  }

  private fun handleNavigation(event: DrawerManager.NavigationEvent) {
      when (event) {
          is DrawerManager.NavigationEvent.Home -> navigateToHome()
          is DrawerManager.NavigationEvent.Calendar -> {
                currentListId = null
                binding.fabAddTask.hide()
                showScreen(app.polar.ui.fragment.CalendarFragment(), getString(R.string.calendar), DrawerManager.Destination.CALENDAR)
          }
          is DrawerManager.NavigationEvent.Tags -> {
                currentListId = null
                binding.fabAddTask.hide()
                showScreen(app.polar.ui.fragment.TagsFragment(), getString(R.string.all_tags), DrawerManager.Destination.TAGS)
          }
          is DrawerManager.NavigationEvent.Reminders -> openReminders()
          is DrawerManager.NavigationEvent.Eisenhower -> {
                currentListId = null
                binding.fabAddTask.hide()
                showScreen(app.polar.ui.fragment.EisenhowerFragment(), getString(R.string.eisenhower_matrix), DrawerManager.Destination.EISENHOWER)
          }
          is DrawerManager.NavigationEvent.Trash -> {
                currentListId = -3L
                binding.fabAddTask.hide()
                showScreen(app.polar.ui.fragment.TrashFragment(), getString(R.string.trash), DrawerManager.Destination.TRASH)
          }
          is DrawerManager.NavigationEvent.Stats -> openStats()
          is DrawerManager.NavigationEvent.Settings -> openSettings()
          is DrawerManager.NavigationEvent.TaskListSelected -> {
                val taskList = event.list
                currentListId = taskList.id
                taskListViewModel.selectList(taskList.id)
                taskViewModel.loadTasksForList(taskList.id)
                showScreen(TasksFragment(), taskList.title, DrawerManager.Destination.LIST)
                binding.fabAddTask.show()
          }
           is DrawerManager.NavigationEvent.CreateList -> {
                showCreateListDialog()
           }
           is DrawerManager.NavigationEvent.EditList -> {
                showEditListDialog(event.list)
           }
      }
  }

  
  private fun openReminders() {
      currentListId = REMINDERS_LIST_ID
      binding.fabAddTask.show()
      showScreen(app.polar.ui.fragment.RemindersFragment(), getString(R.string.reminders), DrawerManager.Destination.REMINDERS)
  }

  private fun openStats() {
      binding.fabAddTask.hide()
      currentListId = null
      showScreen(app.polar.ui.fragment.StatsFragment(), getString(R.string.statistics), DrawerManager.Destination.STATS)
  }

  private fun openSettings() {
      binding.fabAddTask.hide()
      currentListId = null
      showScreen(app.polar.ui.fragment.SettingsFragment(), getString(R.string.settings), DrawerManager.Destination.SETTINGS)
  }
  
  private fun navigateToHome() {
      currentListId = -1L
      taskViewModel.loadAllTasks()
      binding.fabAddTask.hide()
      showScreen(TasksFragment(), getString(R.string.home), DrawerManager.Destination.HOME)
  }
  
  private fun setupFab() {
    binding.fabAddTask.setOnClickListener {
      if (currentListId == REMINDERS_LIST_ID) {
          app.polar.ui.dialog.ReminderDialog(
              onSaveWithLocation = { title, desc, time, lat, lng, radius, locName ->
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
      listId = listId,
      onSave = { result ->
        // "@lista" en el título puede crear la tarea en otra lista
        taskViewModel.insertTask(
          result.listId ?: listId, result.title, result.description, result.tags, result.subtasks,
          result.dueDate, result.recurrence, result.priority, result.timeEstimate
        )
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

  companion object {
    const val EXTRA_NAVIGATE_TO_REMINDERS = "NAVIGATE_TO_REMINDERS"
    // Id especial de currentListId para la pantalla de recordatorios.
    private const val REMINDERS_LIST_ID = -2L
    private const val STATE_CURRENT_LIST_ID = "state_current_list_id"
    private const val STATE_TOOLBAR_TITLE = "state_toolbar_title"
    private const val STATE_DESTINATION = "state_destination"
  }
  
}