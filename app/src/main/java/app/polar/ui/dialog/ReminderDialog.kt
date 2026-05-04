package app.polar.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import app.polar.databinding.DialogReminderBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderDialog(
    private val reminder: app.polar.data.entity.Reminder? = null,
    private val onSave: ((String, String, Long) -> Unit)? = null,
    private val onSaveWithLocation: ((String, String, Long, Double?, Double?, Float?, String?) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogReminderBinding? = null
    private val binding get() = _binding!!
    
    private var selectedDate: Long = System.currentTimeMillis()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvDialogTitle.text = if (reminder == null) getString(app.polar.R.string.new_reminder) else getString(app.polar.R.string.edit_reminder)
        
        if (reminder != null) {
            binding.etTitle.setText(reminder.title)
            binding.etDescription.setText(reminder.description)
            selectedDate = reminder.dateTime
            binding.etLocationName.setText(reminder.locationName ?: "")
            binding.etLatitude.setText(reminder.latitude?.toString() ?: "")
            binding.etLongitude.setText(reminder.longitude?.toString() ?: "")
            updateDateDisplay()
        } else {
             // Default to +1 hour? or current time.
             updateDateDisplay()
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            if (title.isBlank()) {
                binding.etTitle.error = "Required"
                return@setOnClickListener
            }
            val description = binding.etDescription.text.toString()
            
            val locName = binding.etLocationName.text.toString().trim().takeIf { it.isNotEmpty() }
            val lat = binding.etLatitude.text.toString().trim().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().trim().toDoubleOrNull()
            
            if (onSaveWithLocation != null) {
                onSaveWithLocation.invoke(title, description, selectedDate, lat, lng, 100f, locName)
            } else {
                onSave?.invoke(title, description, selectedDate)
            }
            dismiss()
        }
        
        binding.btnCancel.setOnClickListener { dismiss() }
        
        binding.btnDate.setOnClickListener {
            showDateTimePicker()
        }
    }
    
    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(app.polar.R.string.select_date))
            .setSelection(selectedDate)
            .build()
            
        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            // Time Picker
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dateSelection
            
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(12)
                .setMinute(0)
                .setTitleText(getString(app.polar.R.string.select_time))
                .build()
                
            timePicker.addOnPositiveButtonClickListener {
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                selectedDate = calendar.timeInMillis
                updateDateDisplay()
            }
            
            timePicker.show(parentFragmentManager, "TimePicker")
        }
        
        datePicker.show(parentFragmentManager, "DatePicker")
    }
    
    private fun updateDateDisplay() {
        val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        // Apply lowercase and replace dots for visual consistency with tasks
        val formattedDate = format.format(java.util.Date(selectedDate)).lowercase().replace(".", "")
        binding.tvDueDate.text = formattedDate
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
