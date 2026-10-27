package app.polar.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Paint
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Reminder
import app.polar.data.entity.Task
import app.polar.databinding.ItemReminderSectionBinding
import app.polar.databinding.ItemTrashReminderBinding
import app.polar.databinding.ItemTrashTaskBinding
import app.polar.util.DateUtils
import com.google.android.material.color.MaterialColors
import java.util.Date
import java.util.Locale

/** Filas de la papelera: una cabecera por tipo seguida de sus elementos. */
sealed class TrashItem {
    enum class Kind { TASKS, REMINDERS }

    data class Header(val kind: Kind, val count: Int) : TrashItem()
    data class DeletedTask(val task: Task, val listTitle: String? = null, val listIcon: String? = null) : TrashItem()
    data class DeletedReminder(val reminder: Reminder) : TrashItem()
}

/**
 * Tarjetas de la papelera con un diseño distinto por tipo para que se sepa de un vistazo
 * qué se va a restaurar o eliminar: las tareas llevan una baldosa cuadrada neutra con el icono
 * de su lista y los recordatorios una baldosa circular tonal con una campana.
 */
class TrashAdapter(
    private val onItemMenu: (TrashItem, View) -> Unit
) : ListAdapter<TrashItem, RecyclerView.ViewHolder>(TrashDiffCallback()) {

    fun isDeletedItem(position: Int): Boolean = currentList.getOrNull(position).let {
        it is TrashItem.DeletedTask || it is TrashItem.DeletedReminder
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TrashItem.Header -> TYPE_HEADER
        is TrashItem.DeletedTask -> TYPE_TASK
        is TrashItem.DeletedReminder -> TYPE_REMINDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemReminderSectionBinding.inflate(inflater, parent, false))
            TYPE_TASK -> TaskViewHolder(ItemTrashTaskBinding.inflate(inflater, parent, false))
            else -> ReminderViewHolder(ItemTrashReminderBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TrashItem.Header -> (holder as HeaderViewHolder).bind(item)
            is TrashItem.DeletedTask -> (holder as TaskViewHolder).bind(item)
            is TrashItem.DeletedReminder -> (holder as ReminderViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(private val binding: ItemReminderSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: TrashItem.Header) {
            val context = binding.root.context
            val label = if (header.kind == TrashItem.Kind.TASKS) R.string.tasks else R.string.reminders
            binding.tvSection.text = context.getString(R.string.reminders_section_count, context.getString(label), header.count)
        }
    }

    inner class TaskViewHolder(private val binding: ItemTrashTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context
        private val foreground = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        private val secondary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        private val priorityHigh = MaterialColors.getColor(binding.root, R.attr.colorPriorityHigh)
        private val priorityMedium = MaterialColors.getColor(binding.root, R.attr.colorPriorityMedium)
        private val priorityLow = MaterialColors.getColor(binding.root, R.attr.colorPriorityLow)

        fun bind(item: TrashItem.DeletedTask) {
            val task = item.task
            resetSwipeState(binding.root)

            // Icono de la lista de origen (se guarda por nombre); si no existe, el de tarea.
            val listIconRes = item.listIcon
                ?.let { context.resources.getIdentifier(it, "drawable", context.packageName) }
                ?.takeIf { it != 0 } ?: R.drawable.ic_check_box
            binding.ivTypeIcon.setImageResource(listIconRes)
            binding.tvType.text = if (item.listTitle.isNullOrBlank()) context.getString(R.string.trash_type_task)
                else context.getString(R.string.trash_type_task_in_list, item.listTitle)

            bindTitle(binding.tvTitle, task.title, task.completed, foreground, secondary)
            binding.tvDescription.text = task.description
            binding.tvDescription.isVisible = task.description.isNotBlank()

            val dueDate = task.dueDate
            binding.tvDueDate.isVisible = dueDate != null
            if (dueDate != null) binding.tvDueDate.text = DateUtils.formatTaskDate(context, dueDate)

            val (priorityLabel, priorityColor) = when (task.priority) {
                3 -> R.string.priority_high to priorityHigh
                2 -> R.string.priority_medium to priorityMedium
                1 -> R.string.priority_low to priorityLow
                else -> null to 0
            }
            binding.tvPriority.isVisible = priorityLabel != null
            if (priorityLabel != null) {
                binding.tvPriority.text = context.getString(priorityLabel)
                TextViewCompat.setCompoundDrawableTintList(binding.tvPriority, ColorStateList.valueOf(priorityColor))
            }
            binding.layoutMeta.isVisible = binding.tvDueDate.isVisible || binding.tvPriority.isVisible

            bindMenu(binding.root, binding.btnMenu, item, task.title)
        }
    }

    inner class ReminderViewHolder(private val binding: ItemTrashReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context
        private val foreground = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        private val secondary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)

        fun bind(item: TrashItem.DeletedReminder) {
            val reminder = item.reminder
            resetSwipeState(binding.root)

            bindTitle(binding.tvTitle, reminder.title, reminder.isCompleted, foreground, secondary)
            binding.tvDescription.text = reminder.description
            binding.tvDescription.isVisible = reminder.description.isNotBlank()

            val locale = Locale.getDefault()
            binding.tvTime.text = DateFormat.getTimeFormat(context).format(Date(reminder.dateTime)).lowercase(locale)
            binding.tvDate.text = DateUtils.formatTaskDate(context, reminder.dateTime)
            binding.tvLocation.text = reminder.locationName
            binding.tvLocation.isVisible = !reminder.locationName.isNullOrBlank()

            bindMenu(binding.root, binding.btnMenu, item, reminder.title)
        }
    }

    // ItemTouchHelper deja la tarjeta desplazada si se cancela un deslizamiento.
    private fun resetSwipeState(root: View) {
        root.translationX = 0f
        root.alpha = 1f
    }

    // Los elementos completados se muestran tachados y atenuados, como en sus pantallas.
    private fun bindTitle(view: TextView, title: String, completed: Boolean, foreground: Int, secondary: Int) {
        view.text = title
        view.setTextColor(if (completed) secondary else foreground)
        view.paintFlags = if (completed) view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }

    // Tocar la tarjeta o el botón de opciones abre el mismo menú (restaurar / eliminar para siempre).
    private fun bindMenu(root: View, button: View, item: TrashItem, title: String) {
        button.contentDescription = root.context.getString(R.string.reminder_options_named, title)
        button.setOnClickListener { onItemMenu(item, button) }
        root.setOnClickListener { onItemMenu(item, button) }
    }

    class TrashDiffCallback : DiffUtil.ItemCallback<TrashItem>() {
        override fun areItemsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean = when {
            oldItem is TrashItem.Header && newItem is TrashItem.Header -> oldItem.kind == newItem.kind
            oldItem is TrashItem.DeletedTask && newItem is TrashItem.DeletedTask -> oldItem.task.id == newItem.task.id
            oldItem is TrashItem.DeletedReminder && newItem is TrashItem.DeletedReminder -> oldItem.reminder.id == newItem.reminder.id
            else -> false
        }

        override fun areContentsTheSame(oldItem: TrashItem, newItem: TrashItem): Boolean = oldItem == newItem
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TASK = 1
        const val TYPE_REMINDER = 2
    }
}
