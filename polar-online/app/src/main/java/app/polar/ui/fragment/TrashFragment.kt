package app.polar.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import app.polar.R
import app.polar.data.entity.Reminder
import app.polar.data.entity.Task
import app.polar.data.entity.TaskList
import app.polar.databinding.FragmentTrashBinding
import app.polar.ui.adapter.TrashAdapter
import app.polar.ui.adapter.TrashItem
import app.polar.ui.viewmodel.RemindersViewModel
import app.polar.ui.viewmodel.TaskListViewModel
import app.polar.ui.viewmodel.TaskViewModel
import app.polar.ui.widget.applyPolarStyle
import app.polar.util.TaskSwipeHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrashFragment : Fragment() {
    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!

    private val taskViewModel: TaskViewModel by activityViewModels()
    private val remindersViewModel: RemindersViewModel by activityViewModels()
    private val taskListViewModel: TaskListViewModel by activityViewModels()
    private lateinit var trashAdapter: TrashAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    private var deletedTasks: List<Task> = emptyList()
    private var deletedReminders: List<Reminder> = emptyList()
    private var listsById: Map<Long, TaskList> = emptyMap()

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
        trashAdapter = TrashAdapter { item, anchor -> showItemMenu(item, anchor) }

        binding.recyclerTrash.layoutManager = LinearLayoutManager(context)
        binding.recyclerTrash.adapter = trashAdapter

        // Derecha -> restaurar. Izquierda -> eliminar para siempre (con confirmación, no se puede deshacer).
        // Las cabeceras de sección no se deslizan.
        val swipeHelper = TaskSwipeHelper(
            rightSwipeConfig = TaskSwipeHelper.SwipeConfig(
                R.attr.colorSuccess, R.drawable.ic_undo, R.attr.colorOnSuccess, R.string.trash_restore
            ),
            leftSwipeConfig = TaskSwipeHelper.SwipeConfig(
                R.attr.colorError, R.drawable.ic_trash, com.google.android.material.R.attr.colorOnError, R.string.delete
            ),
            getSwipeFlagsForHolder = { holder ->
                if (trashAdapter.isDeletedItem(holder.bindingAdapterPosition)) ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT else 0
            },
            cornerRadiusDp = 16f,
            onSwipedRight = { position ->
                val item = trashAdapter.currentList.getOrNull(position)
                // Restablecer aunque falle la escritura: la fila desaparece al salir de la papelera.
                resetSwipe()
                if (item != null) restore(item)
            },
            onSwipedLeft = { position ->
                val item = trashAdapter.currentList.getOrNull(position)
                // La tarjeta vuelve a su sitio; solo desaparece si se confirma el borrado.
                resetSwipe()
                if (item != null) confirmPermanentDelete(item)
            }
        )

        itemTouchHelper = ItemTouchHelper(swipeHelper).also { it.attachToRecyclerView(binding.recyclerTrash) }
    }

    private fun resetSwipe() {
        // Desacoplar ItemTouchHelper limpia la animación de salida y devuelve la tarjeta a su sitio.
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper?.attachToRecyclerView(binding.recyclerTrash)
    }

    private fun showItemMenu(item: TrashItem, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.menu_trash_item, menu)
            applyPolarStyle(requireContext())
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_restore -> { restore(item); true }
                    R.id.action_delete -> { confirmPermanentDelete(item); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun restore(item: TrashItem) {
        val message = when (item) {
            is TrashItem.DeletedTask -> {
                taskViewModel.restoreFromTrash(item.task)
                R.string.trash_task_restored
            }
            is TrashItem.DeletedReminder -> {
                remindersViewModel.restoreFromTrash(item.reminder)
                R.string.trash_reminder_restored
            }
            is TrashItem.Header -> return
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    // El diálogo nombra el tipo y el título para que quede claro qué se borra para siempre.
    private fun confirmPermanentDelete(item: TrashItem) {
        val (title, name) = when (item) {
            is TrashItem.DeletedTask -> R.string.trash_confirm_delete_task to item.task.title
            is TrashItem.DeletedReminder -> R.string.trash_confirm_delete_reminder to item.reminder.title
            is TrashItem.Header -> return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(getString(R.string.trash_confirm_delete_message, name))
            .setPositiveButton(R.string.delete) { _, _ -> permanentDelete(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun permanentDelete(item: TrashItem) {
        val message = when (item) {
            is TrashItem.DeletedTask -> {
                taskViewModel.permanentDelete(item.task)
                R.string.trash_task_deleted
            }
            is TrashItem.DeletedReminder -> {
                remindersViewModel.permanentDelete(item.reminder)
                R.string.trash_reminder_deleted
            }
            is TrashItem.Header -> return
        }
        _binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_SHORT).show() }
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

        // Nombre e icono de la lista de origen de cada tarea.
        taskListViewModel.allTaskLists.observe(viewLifecycleOwner) { lists ->
            listsById = lists.associateBy { it.id }
            updateList()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        // Una sección por tipo, con su contador; las vacías no se muestran.
        val items = buildList {
            if (deletedTasks.isNotEmpty()) {
                add(TrashItem.Header(TrashItem.Kind.TASKS, deletedTasks.size))
                deletedTasks.forEach { task ->
                    val list = listsById[task.listId]
                    add(TrashItem.DeletedTask(task, list?.title, list?.icon))
                }
            }
            if (deletedReminders.isNotEmpty()) {
                add(TrashItem.Header(TrashItem.Kind.REMINDERS, deletedReminders.size))
                deletedReminders.forEach { add(TrashItem.DeletedReminder(it)) }
            }
        }

        trashAdapter.submitList(items)

        val isEmpty = items.isEmpty()
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        // Sin elementos no tiene sentido vaciar la papelera.
        binding.btnEmptyTrash.visibility = if (isEmpty) View.INVISIBLE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper = null
        _binding = null
    }
}
