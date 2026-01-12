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
    private val onSave: (String, String, Long) -> Unit
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
        
        binding.tvDialogTitle.text = if (reminder == null) "New Reminder" else "Edit Reminder"
        
        if (reminder != null) {
            binding.etTitle.setText(reminder.title)
            binding.etDescription.setText(reminder.description)
            selectedDate = reminder.dateTime
            updateDateDisplay()
        } else {
             // Default to +1 hour? or current time.
             updateDateDisplay()
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            if (title.isBlank()) {
                // Fixed: The simpler layout doesn't use TextInputLayout IDs for errors 
                // effectively unless we add them or just error on EditText
                binding.etTitle.error = "Required"
                return@setOnClickListener
            }
            val description = binding.etDescription.text.toString()
            onSave(title, description, selectedDate)
            dismiss()
        }
        
        binding.btnCancel.setOnClickListener { dismiss() }
        
        binding.btnDate.setOnClickListener {
            showDateTimePicker()
        }
    }
    
    // Copy of DatePicker logic
    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(selectedDate)
            .build()
            
        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            // Time Picker
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dateSelection
            
            // Should get current time if date is today?
            // Just default to 12:00
            
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(12)
                .setMinute(0)
                .setTitleText("Select time")
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
        binding.tvDueDate.text = format.format(java.util.Date(selectedDate))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
