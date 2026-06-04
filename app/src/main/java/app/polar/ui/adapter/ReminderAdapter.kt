package app.polar.ui.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.polar.data.entity.Reminder
import app.polar.databinding.ItemReminderBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReminderAdapter(
    private val onCheckChanged: (Reminder, Boolean, android.view.View) -> Unit,
    private val onItemClick: (Reminder) -> Unit,
    private val onItemLongClick: (Reminder, android.view.View) -> Boolean
) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    private var reminders: List<Reminder> = emptyList()

    fun submitList(newReminders: List<Reminder>) {
        val diffCallback = ReminderDiffCallback(reminders, newReminders)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        reminders = newReminders
        diffResult.dispatchUpdatesTo(this)
    }

    fun getItem(position: Int): Reminder = reminders[position]

    override fun getItemCount(): Int = reminders.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = getItem(position)
        holder.bind(reminder, onCheckChanged, onItemClick, onItemLongClick)
    }

    class ReminderViewHolder(private val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            reminder: Reminder,
            onCheckChanged: (Reminder, Boolean, android.view.View) -> Unit,
            onItemClick: (Reminder) -> Unit,
            onItemLongClick: (Reminder, android.view.View) -> Boolean
        ) {
            // Reset any stale visual state from previous swipe/animation
            binding.root.animate().cancel()
            binding.root.translationX = 0f
            binding.root.translationY = 0f
            binding.root.alpha = 1.0f
            binding.root.scaleX = 1.0f
            binding.root.scaleY = 1.0f

            binding.tvReminderTitle.text = reminder.title

            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            binding.tvReminderTime.text = dateFormat.format(java.util.Date(reminder.dateTime))

            val hasLocation = reminder.locationName != null && reminder.locationName.isNotEmpty()
            if (hasLocation) {
                binding.layoutLocation.visibility = android.view.View.VISIBLE
                binding.tvReminderLocation.text = reminder.locationName
                binding.layoutLocation.isClickable = reminder.latitude != null && reminder.longitude != null
                binding.layoutLocation.isFocusable = reminder.latitude != null && reminder.longitude != null
                if (binding.layoutLocation.isClickable) {
                    binding.layoutLocation.setOnClickListener {
                        openLocationInMaps(it.context, reminder.latitude!!, reminder.longitude!!, reminder.locationName)
                    }
                } else {
                    binding.layoutLocation.setOnClickListener(null)
                }
            } else {
                binding.layoutLocation.visibility = android.view.View.GONE
                binding.layoutLocation.setOnClickListener(null)
            }

            // Remove listener to avoid triggering loop
            binding.cbReminderComplete.setOnCheckedChangeListener(null)
            binding.cbReminderComplete.isChecked = reminder.isCompleted

            // Apply visual effects based on completion status
            if (reminder.isCompleted) {
                // Strikethrough title
                binding.tvReminderTitle.paintFlags = binding.tvReminderTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

                // Reduce opacity of entire card
                binding.root.alpha = 0.5f

                // Reduce opacity of icon container
                binding.iconContainer?.alpha = 0.4f

                // Dim text colors
                binding.tvReminderTitle.alpha = 0.6f
                binding.tvReminderTime.alpha = 0.5f
            } else {
                // Remove strikethrough
                binding.tvReminderTitle.paintFlags = binding.tvReminderTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()

                // Full opacity
                binding.root.alpha = 1.0f

                // Full opacity for icon container
                binding.iconContainer?.alpha = 1.0f

                // Full opacity for text
                binding.tvReminderTitle.alpha = 1.0f
                binding.tvReminderTime.alpha = 1.0f
            }

            binding.cbReminderComplete.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != reminder.isCompleted) {
                    onCheckChanged(reminder, isChecked, binding.root)
                }
            }

            // Manual right-swipe handling to avoid ItemTouchHelper bugs
            // when the dataset size doesn't change (toggle completion).
            binding.root.setOnTouchListener(object : android.view.View.OnTouchListener {
                private var downX = 0f
                private var downY = 0f
                private var isDragging = false

                override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            isDragging = false
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - downX
                            val deltaY = event.rawY - downY
                            if (!isDragging) {
                                if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 10f) {
                                    isDragging = true
                                    v.parent.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            if (isDragging && deltaX > 0) {
                                v.translationX = deltaX
                                return true
                            }
                            return false
                        }
                        MotionEvent.ACTION_UP -> {
                            val deltaX = event.rawX - downX
                            if (isDragging) {
                                v.parent.requestDisallowInterceptTouchEvent(false)
                                if (deltaX > 150f) {
                                    onCheckChanged(reminder, !reminder.isCompleted, v)
                                }
                                v.animate().translationX(0f).setDuration(200).start()
                                isDragging = false
                                return true
                            }
                            return false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (isDragging) {
                                v.parent.requestDisallowInterceptTouchEvent(false)
                                v.animate().translationX(0f).setDuration(200).start()
                                isDragging = false
                            }
                            return false
                        }
                    }
                    return false
                }
            })

            binding.root.setOnClickListener { onItemClick(reminder) }
            binding.root.setOnLongClickListener {
                onItemLongClick(reminder, it)
            }
        }

        private fun openLocationInMaps(context: android.content.Context, lat: Double, lng: Double, name: String?) {
            val uri = if (!name.isNullOrBlank()) {
                android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng($name)")
            } else {
                android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val genericIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                context.startActivity(genericIntent)
            }
        }
    }

    class ReminderDiffCallback(
        private val oldList: List<Reminder>,
        private val newList: List<Reminder>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition].id == newList[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
