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
                app.polar.ui.dialog.ReminderDialog(
                    reminder = reminder,
                    onSave = { title, desc, time ->
                         viewModel.update(reminder.copy(title = title, description = desc, dateTime = time))
                    }
                ).show(parentFragmentManager, "EditReminderDialog")
            }
        )
        binding.recyclerReminders.layoutManager = LinearLayoutManager(context)
        binding.recyclerReminders.adapter = adapter
    }

    private fun observeReminders() {
        // Show active reminders primarily? or all? 
        // User asked for "create lists of tasks... create reminders not linked to tasks"
        // Usually reminders are transient. Completed ones disappear or move to bottom.
        // Let's observe all sorted by time, but maybe visual distinction handled in adapter.
        // Or activeReminders? Let's use allReminders for now so user can see history or uncheck.
        // Actually, maybe distinct tab? Let's stick to allReminders.
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
