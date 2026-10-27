package app.polar.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import app.polar.R
import app.polar.databinding.BottomSheetSnoozeBinding
import app.polar.databinding.ItemSnoozeOptionBinding
import app.polar.util.SnoozeOption
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Hoja para elegir hasta cuándo se pospone un aviso: opciones rápidas con la hora resultante y una
 * fecha y hora a medida. Devuelve la elección con la Fragment Result API ([REQUEST_KEY]): el
 * resultado lleva [RESULT_UNTIL] o, si se cierra sin elegir, va vacío.
 */
class SnoozeSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSnoozeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSnoozeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val subtitle = arguments?.getString(ARG_SUBTITLE)
        binding.tvSnoozeSubtitle.text = subtitle
        binding.tvSnoozeSubtitle.isVisible = !subtitle.isNullOrBlank()

        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val defaultHour = prefs.getInt("default_notification_hour", 7)
        val defaultMinute = prefs.getInt("default_notification_minute", 30)
        val now = System.currentTimeMillis()

        SnoozeOption.entries.forEach { option ->
            val until = option.at(now, defaultHour, defaultMinute) ?: return@forEach
            addOption(iconOf(option), labelOf(option), timeLabel(option, until)) { deliver(until) }
        }
        addOption(R.drawable.ic_calendar, getString(R.string.snooze_pick), null) { pickDateTime() }
    }

    private fun addOption(@DrawableRes icon: Int, label: String, time: String?, onClick: () -> Unit) {
        val row = ItemSnoozeOptionBinding.inflate(layoutInflater, binding.containerSnoozeOptions, true)
        row.ivSnoozeOptionIcon.setImageResource(icon)
        row.tvSnoozeOptionLabel.text = label
        row.tvSnoozeOptionTime.text = time
        row.tvSnoozeOptionTime.isVisible = time != null
        row.root.contentDescription = listOfNotNull(label, time).joinToString(", ")
        row.root.setOnClickListener { onClick() }
    }

    private fun iconOf(option: SnoozeOption): Int = when (option) {
        SnoozeOption.TOMORROW -> R.drawable.ic_calendar_mini
        else -> R.drawable.ic_schedule
    }

    private fun labelOf(option: SnoozeOption): String = when (option) {
        SnoozeOption.MINUTES_15 -> resources.getQuantityString(R.plurals.snooze_in_minutes, 15, 15)
        SnoozeOption.HOUR_1 -> resources.getQuantityString(R.plurals.snooze_in_hours, 1, 1)
        SnoozeOption.HOURS_3 -> resources.getQuantityString(R.plurals.snooze_in_hours, 3, 3)
        SnoozeOption.THIS_EVENING -> getString(R.string.snooze_this_evening)
        SnoozeOption.TOMORROW -> getString(R.string.tomorrow)
    }

    // La hora basta mientras sea hoy (o "mañana", que ya lo dice el texto); si una opción pasa de
    // medianoche ("en 3 horas" a las 23:00), se indica también el día.
    private fun timeLabel(option: SnoozeOption, until: Long): String =
        if (option == SnoozeOption.TOMORROW || android.text.format.DateUtils.isToday(until)) {
            DateFormat.getTimeFormat(requireContext()).format(Date(until))
        } else {
            app.polar.util.DateUtils.formatDateTime(requireContext(), until)
        }

    private fun pickDateTime() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.from(MaterialDatePicker.todayInUtcMilliseconds()))
                    .build()
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            // El selector devuelve la medianoche UTC del día elegido: se pasa al mismo día local.
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection }
            val suggested = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(suggested.get(Calendar.HOUR_OF_DAY))
                .setMinute(0)
                .setTitleText(getString(R.string.select_time))
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val until = Calendar.getInstance().apply {
                    clear()
                    set(
                        utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                        timePicker.hour, timePicker.minute
                    )
                }.timeInMillis
                if (until <= System.currentTimeMillis()) {
                    Toast.makeText(requireContext(), R.string.snooze_pick_past, Toast.LENGTH_SHORT).show()
                } else {
                    deliver(until)
                }
            }
            timePicker.show(childFragmentManager, "SnoozeTimePicker")
        }
        datePicker.show(childFragmentManager, "SnoozeDatePicker")
    }

    private fun deliver(until: Long) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_UNTIL to until))
        dismiss()
    }

    // Solo al cerrarla el usuario (atrás, fuera o deslizando), no al recrearse por un giro.
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SnoozeSheet"
        const val REQUEST_KEY = "snooze_sheet_result"
        const val RESULT_UNTIL = "until"
        private const val ARG_SUBTITLE = "subtitle"

        /** @param subtitle título de la tarea o del recordatorio que se pospone. */
        fun newInstance(subtitle: String?) = SnoozeSheet().apply {
            arguments = bundleOf(ARG_SUBTITLE to subtitle)
        }

        /** Momento elegido en un resultado de [REQUEST_KEY], o `null` si se cerró sin elegir. */
        fun untilFrom(result: Bundle): Long? =
            if (result.containsKey(RESULT_UNTIL)) result.getLong(RESULT_UNTIL) else null
    }
}
