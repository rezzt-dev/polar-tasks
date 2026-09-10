package app.polar.ui.fragment

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.Reminder
import app.polar.databinding.FragmentRemindersBinding
import app.polar.ui.adapter.ReminderAdapter
import app.polar.ui.dialog.ReminderDialog
import app.polar.ui.model.ReminderFilter
import app.polar.ui.model.ReminderPresentation
import app.polar.ui.model.ReminderRow
import app.polar.ui.viewmodel.RemindersViewModel
import app.polar.util.TaskSwipeHelper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date

@AndroidEntryPoint
class RemindersFragment : Fragment() {
    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()
    private lateinit var adapter: ReminderAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var reminders: List<Reminder> = emptyList()
    private var selectedFilter = ReminderFilter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedFilter = savedInstanceState?.getString(FILTER_KEY)?.let { name ->
            ReminderFilter.entries.firstOrNull { it.name == name }
        } ?: selectedFilter
        adapter = ReminderAdapter(
            onCheckChanged = { reminder, checked, _ -> viewModel.update(reminder.copy(isCompleted = checked)) },
            onItemClick = ::showEditReminderDialog,
            onItemLongClick = { reminder, anchor -> showReminderPopupMenu(reminder, anchor); true }
        )
        binding.recyclerReminders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReminders.adapter = adapter
        // La tarjeta se conserva al completar; evita conflictos entre la recuperación del swipe y el diff.
        binding.recyclerReminders.itemAnimator = null
        binding.filterGroup.check(when (selectedFilter) {
            ReminderFilter.ALL -> R.id.chipAll
            ReminderFilter.PENDING -> R.id.chipPending
            ReminderFilter.COMPLETED -> R.id.chipCompleted
        })
        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedFilter = when (checkedIds.firstOrNull()) {
                R.id.chipPending -> ReminderFilter.PENDING
                R.id.chipCompleted -> ReminderFilter.COMPLETED
                else -> ReminderFilter.ALL
            }
            updateUI()
        }
        setupSwipeGestures()
        observeReminders()
    }

    private fun setupSwipeGestures() {
        val deleteConfig = TaskSwipeHelper.SwipeConfig(
            R.attr.colorError, R.drawable.ic_trash, com.google.android.material.R.attr.colorOnError, R.string.delete
        )
        val swipe = TaskSwipeHelper(
            leftSwipeConfig = deleteConfig,
            getSwipeFlagsForHolder = { holder ->
                if (adapter.reminderAt(holder.bindingAdapterPosition) != null) ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT else 0
            },
            swipeConfigForHolder = { holder, right ->
                if (!right) deleteConfig else {
                    val completed = adapter.reminderAt(holder.bindingAdapterPosition)?.isCompleted == true
                    TaskSwipeHelper.SwipeConfig(
                        R.attr.colorSuccess,
                        if (completed) R.drawable.ic_undo else R.drawable.ic_check,
                        R.attr.colorOnSuccess,
                        if (completed) R.string.reminder_reactivate else R.string.reminder_complete
                    )
                }
            },
            cornerRadiusDp = 20f,
            onSwipedRight = { position ->
                adapter.reminderAt(position)?.let { reminder ->
                    // Restablecer incluso si falla la escritura o el elemento sigue en el filtro actual.
                    resetSwipe()
                    viewModel.update(reminder.copy(isCompleted = !reminder.isCompleted))
                }
            },
            onSwipedLeft = { position ->
                adapter.reminderAt(position)?.let { reminder ->
                    resetSwipe()
                    moveToTrash(reminder)
                }
            }
        )
        itemTouchHelper = ItemTouchHelper(swipe).also { it.attachToRecyclerView(binding.recyclerReminders) }
    }

    private fun resetSwipe() {
        // ItemTouchHelper conserva la animación de salida si el diff mueve la misma fila.
        // Desacoplarlo limpia esa animación y devuelve la tarjeta a su posición original.
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper?.attachToRecyclerView(binding.recyclerReminders)
    }

    private fun moveToTrash(reminder: Reminder) {
        viewModel.moveToTrash(reminder)
        Snackbar.make(binding.root, R.string.reminder_moved_trash, Snackbar.LENGTH_LONG)
            .setAnchorView((activity as? app.polar.MainActivity)?.snackbarAnchor)
            .setAction(R.string.undo) { viewModel.restoreFromTrash(reminder) }.show()
    }

    private fun showEditReminderDialog(reminder: Reminder) {
        ReminderDialog(
            reminder = reminder,
            onSaveWithLocation = { title, desc, time, lat, lng, radius, locName ->
                viewModel.update(reminder.copy(title = title, description = desc, dateTime = time,
                    latitude = lat, longitude = lng, radius = radius, locationName = locName))
            }
        ).show(parentFragmentManager, "EditReminderDialog")
    }

    private fun showReminderPopupMenu(reminder: Reminder, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_task, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> { showEditReminderDialog(reminder); true }
                    R.id.action_delete -> { moveToTrash(reminder); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun observeReminders() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.allReminders.collect {
                        reminders = it.filterNot(Reminder::isDeleted)
                        updateUI()
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        if (error != null) {
                            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
                // Reevalúa vencimientos y cambio de día aunque Room no emita datos nuevos.
                launch {
                    while (isActive) {
                        delay(60_000)
                        if (binding.recyclerReminders.scrollState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) updateUI()
                    }
                }
            }
        }
    }

    private fun updateUI() {
        val now = System.currentTimeMillis()
        val pending = reminders.filterNot(Reminder::isCompleted)
        val overdue = pending.count { it.dateTime < now }
        binding.tvSummary.text = getString(R.string.reminders_summary,
            resources.getQuantityString(R.plurals.reminders_pending_count, pending.size, pending.size),
            resources.getQuantityString(R.plurals.reminders_overdue_count, overdue, overdue))
        val next = pending.filter { it.dateTime >= now }.minByOrNull(Reminder::dateTime)
        binding.nextReminderCard.isVisible = next != null
        binding.nextReminderCard.setOnClickListener(next?.let { reminder -> View.OnClickListener { showEditReminderDialog(reminder) } })
        if (next != null) {
            val date = Date(next.dateTime)
            binding.tvNextLabel.text = getString(R.string.reminders_next,
                DateFormat.getMediumDateFormat(requireContext()).format(date), DateFormat.getTimeFormat(requireContext()).format(date))
            binding.tvNextTitle.text = next.title
        }
        val rows = ReminderPresentation.rows(reminders, selectedFilter, now)
        adapter.submitRows(rows)
        val empty = rows.none { it is ReminderRow.Item }
        binding.emptyState.isVisible = empty
        binding.recyclerReminders.isVisible = !empty
        binding.tvEmptyTitle.setText(when {
            reminders.isEmpty() -> R.string.reminders_empty_title
            selectedFilter == ReminderFilter.COMPLETED -> R.string.reminders_empty_completed
            else -> R.string.reminders_empty_pending
        })
        binding.tvEmptyDescription.setText(when {
            reminders.isEmpty() -> R.string.reminders_empty_description
            selectedFilter == ReminderFilter.COMPLETED -> R.string.reminders_empty_completed_description
            else -> R.string.reminders_empty_pending_description
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FILTER_KEY, selectedFilter.name)
    }

    override fun onDestroyView() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
        binding.recyclerReminders.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object { private const val FILTER_KEY = "reminders_filter" }
}
