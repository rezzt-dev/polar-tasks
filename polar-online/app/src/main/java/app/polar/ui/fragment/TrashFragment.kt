package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.data.entity.Task
import app.polar.databinding.FragmentTrashBinding
import app.polar.ui.adapter.TrashAdapter
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.util.TaskSwipeHelper
import com.google.android.material.snackbar.Snackbar
import app.polar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TrashFragment : Fragment() {
    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by activityViewModels()
    private val remindersViewModel: app.polar.ui.viewmodel.RemindersViewModel by activityViewModels()
    private lateinit var trashAdapter: TrashAdapter
    
    // LiveData separate or Mediator? simpler to just observe both and merge locally
    private var deletedTasks: List<Task> = emptyList()
    private var deletedReminders: List<app.polar.data.entity.Reminder> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeDeletedItems()
        
        binding.btnEmptyTrash.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_empty_trash_title)
                .setMessage(R.string.confirm_empty_trash_message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    taskViewModel.emptyTrash()
                    remindersViewModel.emptyTrash()
                    Snackbar.make(binding.root, getString(R.string.trash_emptied), Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        trashAdapter = TrashAdapter { item ->
            // Click
        }

        binding.recyclerTrash.layoutManager = LinearLayoutManager(context)
        binding.recyclerTrash.adapter = trashAdapter

        // Swipe con feedback visual reutilizando TaskSwipeHelper (mismo aspecto que
        // el swipe de la lista de tareas): fondo de color + icono mientras se desliza.
        // Derecha -> restablecer a la lista. Izquierda -> eliminar permanentemente.
        val swipeHelper = TaskSwipeHelper(
            rightSwipeConfig = TaskSwipeHelper.SwipeConfig(
                backgroundColorAttr = R.attr.colorSuccess,
                iconRes = R.drawable.ic_arrow_back,
                iconTintAttr = R.attr.colorOnSuccess
            ),
            leftSwipeConfig = TaskSwipeHelper.SwipeConfig(
                backgroundColorAttr = R.attr.colorError,
                iconRes = R.drawable.ic_trash,
                iconTintAttr = R.attr.colorOnError
            ),
            onSwipedRight = { position -> handleTrashSwipe(position, restore = true) },
            onSwipedLeft = { position -> handleTrashSwipe(position, restore = false) }
        )

        ItemTouchHelper(swipeHelper).attachToRecyclerView(binding.recyclerTrash)
    }

    private fun handleTrashSwipe(position: Int, restore: Boolean) {
        val item = trashAdapter.currentList.getOrNull(position) ?: return

        if (restore) {
            when (item) {
                is app.polar.ui.adapter.TrashItem.DeletedTask -> taskViewModel.restoreFromTrash(item.task)
                is app.polar.ui.adapter.TrashItem.DeletedReminder -> remindersViewModel.restoreFromTrash(item.reminder)
            }
            Snackbar.make(binding.root, getString(R.string.item_restored), Snackbar.LENGTH_SHORT).show()
        } else {
            when (item) {
                is app.polar.ui.adapter.TrashItem.DeletedTask -> taskViewModel.permanentDelete(item.task)
                is app.polar.ui.adapter.TrashItem.DeletedReminder -> remindersViewModel.permanentDelete(item.reminder)
            }
            Snackbar.make(binding.root, getString(R.string.permanently_deleted), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeDeletedItems() {
        taskViewModel.getDeletedTasks().observe(viewLifecycleOwner) { tasks ->
            deletedTasks = tasks
            updateList()
        }
        
        remindersViewModel.getDeletedReminders().observe(viewLifecycleOwner) { reminders ->
            deletedReminders = reminders
            updateList()
        }

        // Observe error messages (StateFlow)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    taskViewModel.errorMessage.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                            taskViewModel.clearError()
                        }
                    }
                }
                
                launch {
                    remindersViewModel.errorMessage.collect { error ->
                        error?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                            remindersViewModel.clearError()
                        }
                    }
                }
            }
        }
    }
    
    private fun updateList() {
        val items = mutableListOf<app.polar.ui.adapter.TrashItem>()
        items.addAll(deletedTasks.map { app.polar.ui.adapter.TrashItem.DeletedTask(it) })
        items.addAll(deletedReminders.map { app.polar.ui.adapter.TrashItem.DeletedReminder(it) })
        
        trashAdapter.submitList(items)
        
        // Toggle empty view visibility
        // binding.emptyView is now a LinearLayout container
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        
        // Also toggle the "Vaciar papelera" button so you can't click it if empty
        binding.btnEmptyTrash.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
