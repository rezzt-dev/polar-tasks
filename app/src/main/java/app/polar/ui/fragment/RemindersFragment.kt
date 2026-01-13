package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.databinding.FragmentRemindersBinding
import app.polar.ui.adapter.ReminderAdapter
import app.polar.ui.viewmodel.RemindersViewModel

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
        viewModel.allReminders.observe(viewLifecycleOwner) { reminders ->
            if (reminders.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerReminders.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerReminders.visibility = View.VISIBLE
                adapter.submitList(reminders)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
