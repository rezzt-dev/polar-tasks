package app.polar.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import app.polar.databinding.DialogSubtaskDetailBinding

class SubtaskDetailBottomSheet(
    private val subtask: app.polar.data.entity.Subtask,
    private val onEdit: () -> Unit,
    private val onDelete: () -> Unit,
    private val onDueDateSet: (Long?) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogSubtaskDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtaskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvSubtaskDetailTitle.text = subtask.title
        updateDueDateText()
        
        binding.btnEdit.setOnClickListener {
            onEdit()
            dismiss()
        }
        
        binding.btnDelete.setOnClickListener {
            onDelete()
            dismiss()
        }
        
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        binding.containerDueDate.setOnClickListener {
            showDateTimePicker()
        }
    }

    private fun updateDueDateText() {
        if (subtask.dueDate != null) {
            binding.tvSubtaskDueDate.text = app.polar.util.DateUtils.formatTaskDate(requireContext(), subtask.dueDate)
            binding.tvSubtaskDueDate.alpha = 1.0f
        } else {
            binding.tvSubtaskDueDate.text = "sin recordatorio"
            binding.tvSubtaskDueDate.alpha = 0.6f
        }
    }

    private fun showDateTimePicker() {
        val datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText("seleccionar fecha")
            .setSelection(subtask.dueDate ?: com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
            .build()
            
        datePicker.addOnPositiveButtonClickListener { dateSelection ->
            val timePicker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setTitleText("seleccionar hora")
                .build()
                
            timePicker.addOnPositiveButtonClickListener {
                val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                utcCalendar.timeInMillis = dateSelection
                
                val localCalendar = java.util.Calendar.getInstance()
                localCalendar.set(utcCalendar.get(java.util.Calendar.YEAR), utcCalendar.get(java.util.Calendar.MONTH), utcCalendar.get(java.util.Calendar.DAY_OF_MONTH))
                localCalendar.set(java.util.Calendar.HOUR_OF_DAY, timePicker.hour)
                localCalendar.set(java.util.Calendar.MINUTE, timePicker.minute)
                localCalendar.set(java.util.Calendar.SECOND, 0)
                localCalendar.set(java.util.Calendar.MILLISECOND, 0)
                
                onDueDateSet(localCalendar.timeInMillis)
                dismiss()
            }
            timePicker.show(parentFragmentManager, "TIME_PICKER")
        }
        datePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SubtaskDetailBottomSheet"
    }
}
