package app.polar.ui.activity

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.databinding.ActivitySearchResultsBinding
import app.polar.ui.adapter.TaskAdapter
import app.polar.ui.viewmodel.TaskViewModel

import app.polar.ui.activity.BaseActivity

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchResultsActivity : BaseActivity() {

  private lateinit var binding: ActivitySearchResultsBinding
  private val viewModel: TaskViewModel by viewModels()
  private lateinit var taskAdapter: TaskAdapter

  companion object {
    const val EXTRA_SEARCH_QUERY = "search_query"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivitySearchResultsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val query = intent.getStringExtra(EXTRA_SEARCH_QUERY) ?: ""

    setupToolbar(query)
    setupRecyclerView()

    // Solo observar si es la primera creación
    if (savedInstanceState == null) {
      performSearch(query)
    }
  }

  private fun setupToolbar(query: String) {
    binding.toolbar.title = "\"$query\""
    binding.toolbar.subtitle = getString(R.string.search)
    binding.toolbar.setNavigationOnClickListener {
      finish()
    }
  }

  private fun setupRecyclerView() {
    taskAdapter = TaskAdapter(
      lifecycleOwner = this,
      viewModel = viewModel,
      onCheckChanged = { task, _, _ ->
        viewModel.toggleTaskCompletion(task)
      },
      onItemLongClick = { task ->
        // Optionally handle long click
        true
      }
    )

    binding.recyclerSearchResults.apply {
      layoutManager = LinearLayoutManager(this@SearchResultsActivity)
      adapter = taskAdapter
    }
  }

  private fun performSearch(query: String) {
    viewModel.searchTasks(query).observe(this) { results ->
      // Verificar que la Activity no esté finalizando
      if (isFinishing) return@observe

      if (results.isEmpty()) {
        binding.emptyState.visibility = android.view.View.VISIBLE
        binding.recyclerSearchResults.visibility = android.view.View.GONE
      } else {
        binding.emptyState.visibility = android.view.View.GONE
        binding.recyclerSearchResults.visibility = android.view.View.VISIBLE
        val items = results.map { app.polar.ui.adapter.TaskListItem.Item(it) }
        taskAdapter.submitList(items)
      }
    }
  }

}