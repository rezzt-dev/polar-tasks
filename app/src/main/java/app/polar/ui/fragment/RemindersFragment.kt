package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.databinding.FragmentRemindersBinding
import app.polar.ui.adapter.ReminderAdapter
import app.polar.ui.viewmodel.RemindersViewModel

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemindersFragment : Fragment() {
    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: RemindersViewModel by activityViewModels()
    private lateinit var adapter: ReminderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeReminders()
    }

    private fun setupRecyclerView() {
        adapter = ReminderAdapter(
            onCheckChanged = { reminder -> viewModel.toggleCompletion(reminder) },
            onItemClick = { reminder -> 
                showEditReminderDialog(reminder)
            },
            onItemLongClick = { reminder, view ->
                showReminderPopupMenu(reminder, view)
                true
            }
        )
        binding.recyclerReminders.layoutManager = LinearLayoutManager(context)
        binding.recyclerReminders.adapter = adapter
        
        // Setup swipe gestures
        setupSwipeGestures()
    }
    
    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, // No drag
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Swipe left and right
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                direction: Int
            ) {
                val position = viewHolder.bindingAdapterPosition
                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
                
                val reminder = adapter.currentList[position]
                
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // Swipe left: Delete
                        viewModel.moveToTrash(reminder)
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            getString(app.polar.R.string.reminder_moved_trash),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).setAction(getString(app.polar.R.string.undo)) {
                            viewModel.restoreFromTrash(reminder)
                        }.show()
                    }
                    ItemTouchHelper.RIGHT -> {
                        // Swipe right: Toggle completion
                        viewModel.toggleCompletion(reminder)
                    }
                }
            }
        }
        
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerReminders)
    }

    private fun showEditReminderDialog(reminder: app.polar.data.entity.Reminder) {
        app.polar.ui.dialog.ReminderDialog(
            reminder = reminder,
            onSave = { title, desc, time ->
                 viewModel.update(reminder.copy(title = title, description = desc, dateTime = time))
            }
        ).show(parentFragmentManager, "EditReminderDialog")
    }

    private fun showReminderPopupMenu(reminder: app.polar.data.entity.Reminder, view: View) {
        android.widget.PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(app.polar.R.menu.menu_task, menu) // Reusing menu_task (Edit/Delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    app.polar.R.id.action_edit -> {
                        showEditReminderDialog(reminder)
                        true
                    }
                    app.polar.R.id.action_delete -> {
                        viewModel.moveToTrash(reminder)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun observeReminders() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.allReminders.collect { reminders ->
                        updateUI(reminders)
                    }
                }
                
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
    
    private fun updateUI(reminders: List<app.polar.data.entity.Reminder>) {
        if (reminders.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerReminders.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerReminders.visibility = View.VISIBLE
            adapter.submitList(reminders)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
