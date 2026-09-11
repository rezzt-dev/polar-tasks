package app.polar.ui.adapter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.polar.R
import app.polar.data.entity.Reminder
import app.polar.databinding.ItemReminderBinding
import app.polar.databinding.ItemReminderSectionBinding
import app.polar.ui.model.ReminderPresentation
import app.polar.ui.model.ReminderRow
import app.polar.ui.model.ReminderSection
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import java.util.Date

class ReminderAdapter(
    private val onCheckChanged: (Reminder, Boolean, View) -> Unit,
    private val onItemClick: (Reminder) -> Unit,
    private val onItemLongClick: (Reminder, View) -> Boolean,
    private val showMenu: Boolean = true
) : ListAdapter<ReminderRow, RecyclerView.ViewHolder>(DiffCallback) {

    // El calendario comparte las tarjetas, pero no necesita cabeceras de sección.
    fun submitReminders(reminders: List<Reminder>) = super.submitList(reminders.map { ReminderRow.Item(it) })

    fun submitRows(rows: List<ReminderRow>) = super.submitList(rows)

    fun reminderAt(position: Int): Reminder? = (currentList.getOrNull(position) as? ReminderRow.Item)?.reminder

    override fun getItemViewType(position: Int): Int = if (getItem(position) is ReminderRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) SectionViewHolder(ItemReminderSectionBinding.inflate(inflater, parent, false))
            else ReminderViewHolder(ItemReminderBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ReminderRow.Header -> (holder as SectionViewHolder).bind(row)
            is ReminderRow.Item -> (holder as ReminderViewHolder).bind(row.reminder)
        }
    }

    class SectionViewHolder(private val binding: ItemReminderSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ReminderRow.Header) {
            binding.tvSection.text = binding.root.context.getString(
                R.string.reminders_section_count, binding.root.context.getString(sectionLabel(header.section)), header.count
            )
        }
    }

    inner class ReminderViewHolder(private val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context
        private val foreground = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        private val secondary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        private val overdue = MaterialColors.getColor(binding.root, R.attr.colorDateOverdue)
        private val success = MaterialColors.getColor(binding.root, R.attr.colorSuccess)
        private val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)

        fun bind(reminder: Reminder) {
            // No cancelar el ViewPropertyAnimator: pertenece también al ItemAnimator.
            binding.root.translationX = 0f
            binding.root.alpha = 1f
            val section = ReminderPresentation.section(reminder, System.currentTimeMillis())
            val statusColor = when (section) {
                ReminderSection.COMPLETED -> success
                ReminderSection.OVERDUE -> overdue
                else -> primary
            }
            binding.tvReminderStatus.text = context.getString(if (reminder.isCompleted) R.string.reminder_completed else sectionLabel(section))
            binding.tvReminderStatus.setTextColor(statusColor)
            val icon = ContextCompat.getDrawable(context,
                if (reminder.isCompleted) R.drawable.ic_check_circle else R.drawable.ic_notifications
            )?.mutate()?.apply {
                setTint(statusColor)
                val size = (16 * context.resources.displayMetrics.density).toInt()
                setBounds(0, 0, size, size)
            }
            binding.tvReminderStatus.setCompoundDrawablesRelative(icon, null, null, null)
            binding.tvReminderTitle.text = reminder.title
            binding.tvReminderTitle.setTextColor(if (reminder.isCompleted) secondary else foreground)
            binding.tvReminderTitle.paintFlags = if (reminder.isCompleted)
                binding.tvReminderTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else binding.tvReminderTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.tvReminderDescription.text = reminder.description
            binding.tvReminderDescription.isVisible = reminder.description.isNotBlank()
            val date = Date(reminder.dateTime)
            binding.tvReminderTime.text = DateFormat.getTimeFormat(context).format(date)
            binding.tvReminderTime.setTextColor(if (section == ReminderSection.OVERDUE) overdue else foreground)
            binding.tvReminderDate.text = DateFormat.getMediumDateFormat(context).format(date)
            binding.tvReminderLocation.text = reminder.locationName
            binding.tvReminderLocation.isVisible = !reminder.locationName.isNullOrBlank()
            binding.tvReminderLocation.setOnClickListener(null)
            val hasCoordinates = reminder.latitude != null && reminder.longitude != null
            binding.tvReminderLocation.isClickable = hasCoordinates
            binding.tvReminderLocation.isFocusable = hasCoordinates
            if (hasCoordinates) binding.tvReminderLocation.setOnClickListener { openLocation(reminder) }

            binding.cbReminderComplete.setOnCheckedChangeListener(null)
            binding.cbReminderComplete.isChecked = reminder.isCompleted
            binding.cbReminderComplete.contentDescription = context.getString(
                if (reminder.isCompleted) R.string.reminder_reactivate_named else R.string.reminder_complete_named, reminder.title
            )
            binding.cbReminderComplete.setOnCheckedChangeListener { _, checked ->
                if (checked != reminder.isCompleted) onCheckChanged(reminder, checked, binding.root)
            }
            binding.root.setOnClickListener { onItemClick(reminder) }
            binding.root.setOnLongClickListener { onItemLongClick(reminder, it) }
            binding.btnReminderMenu.visibility = if (showMenu) View.VISIBLE else View.INVISIBLE
            binding.btnReminderMenu.setOnClickListener { onItemLongClick(reminder, it) }
            binding.btnReminderMenu.contentDescription = context.getString(R.string.reminder_options_named, reminder.title)
        }

        private fun openLocation(reminder: Reminder) {
            val coordinates = "${reminder.latitude},${reminder.longitude}"
            val query = Uri.encode("$coordinates (${reminder.locationName.orEmpty()})")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=$query")))
            } catch (_: ActivityNotFoundException) {
                Snackbar.make(binding.root, R.string.reminder_no_maps, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        fun sectionLabel(section: ReminderSection): Int = when (section) {
            ReminderSection.OVERDUE -> R.string.reminders_overdue
            ReminderSection.TODAY -> R.string.today
            ReminderSection.TOMORROW -> R.string.tomorrow
            ReminderSection.UPCOMING -> R.string.reminders_upcoming
            ReminderSection.COMPLETED -> R.string.reminders_filter_completed
        }

        private val DiffCallback = object : DiffUtil.ItemCallback<ReminderRow>() {
            override fun areItemsTheSame(old: ReminderRow, new: ReminderRow): Boolean = when {
                old is ReminderRow.Header && new is ReminderRow.Header -> old.section == new.section
                old is ReminderRow.Item && new is ReminderRow.Item -> old.reminder.id == new.reminder.id
                else -> false
            }
            override fun areContentsTheSame(old: ReminderRow, new: ReminderRow): Boolean = old == new
        }
    }
}
