package app.polar.ui.dialog

import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import app.polar.R
import app.polar.databinding.BottomSheetRecurrenceBinding
import app.polar.util.RecurrenceFormatter
import app.polar.util.RecurrenceRule
import app.polar.util.RecurrenceRule.Frequency
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * Editor de repetición personalizada en una hoja inferior: cada N días, semanas, meses o años, en
 * días concretos de la semana o del mes, y contando desde la fecha de la tarea o desde el día en que
 * se completa. El resumen y las próximas fechas se actualizan mientras se edita.
 *
 * @param initial regla actual de la tarea, o `null` si aún no se repite.
 * @param anchorDate fecha de la tarea (o la que tendrá): base de las próximas fechas y del "mismo
 *   día del mes".
 */
class RecurrenceDialog(
    private val context: Context,
    initial: RecurrenceRule?,
    private val anchorDate: Long,
    private val onApply: (RecurrenceRule) -> Unit
) {

    private val locale = RecurrenceFormatter.localeOf(context)
    private val anchor = Calendar.getInstance().apply { timeInMillis = anchorDate }

    // Cada sección conserva su valor al cambiar de unidad; la regla final solo usa los que aplican.
    private var frequency = initial?.frequency ?: Frequency.WEEKLY
    private val weekdays = initial?.weekdays?.toMutableSet()
        ?: mutableSetOf(anchor.get(Calendar.DAY_OF_WEEK))
    private var monthDay: Int? = initial?.monthDay
    private var fromCompletion = initial?.fromCompletion ?: false
    private val initialInterval = initial?.interval ?: 1

    private lateinit var binding: BottomSheetRecurrenceBinding

    private val unitChips by lazy {
        mapOf(
            Frequency.DAILY to binding.chipUnitDay,
            Frequency.WEEKLY to binding.chipUnitWeek,
            Frequency.MONTHLY to binding.chipUnitMonth,
            Frequency.YEARLY to binding.chipUnitYear
        )
    }

    fun show() {
        val dialog = BottomSheetDialog(context)
        binding = BottomSheetRecurrenceBinding.inflate(LayoutInflater.from(context))

        setupInterval()
        setupUnits()
        setupWeekdays()
        setupMonthDays()
        setupAnchor()

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnApply.setOnClickListener {
            val rule = currentRule() ?: return@setOnClickListener
            onApply(rule)
            dialog.dismiss()
        }

        render()
        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    // ---------------------------------------------------------------------------------------------
    // Secciones
    // ---------------------------------------------------------------------------------------------

    private fun setupInterval() {
        binding.etInterval.setText(initialInterval.toString())
        binding.etInterval.doAfterTextChanged { render() }
        binding.btnIntervalDecrease.setOnClickListener { stepInterval(-1) }
        binding.btnIntervalIncrease.setOnClickListener { stepInterval(+1) }
    }

    private fun stepInterval(delta: Int) {
        val value = ((currentInterval() ?: 1) + delta).coerceIn(1, RecurrenceRule.MAX_INTERVAL)
        binding.etInterval.setText(value.toString())
        binding.etInterval.setSelection(binding.etInterval.length())
    }

    private fun setupUnits() {
        unitChips.forEach { (unit, chip) -> chip.setText(RecurrenceFormatter.unitRes(unit)) }
        unitChips.getValue(frequency).isChecked = true
        binding.chipGroupUnit.setOnCheckedStateChangeListener { _, checkedIds ->
            val checked = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            frequency = unitChips.entries.first { it.value.id == checked }.key
            render()
        }
    }

    private fun setupWeekdays() {
        val fullNames = DateFormatSymbols.getInstance(locale).weekdays
        val density = context.resources.displayMetrics.density
        val gap = (4 * density).toInt()
        val days = RecurrenceFormatter.weekdaysInLocaleOrder(locale)
        days.forEachIndexed { index, day ->
            val chip = newChip(RecurrenceFormatter.shortWeekdayName(day, locale)).apply {
                contentDescription = fullNames[day].lowercase(locale)
                gravity = Gravity.CENTER
                // Siete en una fila también en pantallas estrechas: sin el relleno interior del texto
                textStartPadding = 0f
                textEndPadding = 0f
                isChecked = day in weekdays
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) weekdays.add(day) else weekdays.remove(day)
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (index < days.lastIndex) params.marginEnd = gap
            binding.rowWeekdays.addView(chip, params)
        }
    }

    private fun setupMonthDays() {
        val anchorDay = anchor.get(Calendar.DAY_OF_MONTH)
        // "el día 1" y "el primer día" serían la misma opción si la tarea ya cae en día 1
        val options = buildList<Int?> {
            add(null)
            if (anchorDay != 1 || monthDay == 1) add(1)
            add(RecurrenceRule.LAST_DAY_OF_MONTH)
            monthDay?.takeIf { it !in this }?.let(::add)
        }
        val chipsByValue = options.associateWith { value ->
            val text = if (value == null) context.getString(R.string.recurrence_month_day_number, anchorDay)
            else RecurrenceFormatter.monthDayText(context, value)
            newChip(text).also { binding.chipGroupMonthDay.addView(it) }
        }
        (chipsByValue[monthDay] ?: chipsByValue.getValue(null)).isChecked = true
        binding.chipGroupMonthDay.setOnCheckedStateChangeListener { _, checkedIds ->
            val checked = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            monthDay = chipsByValue.entries.first { it.value.id == checked }.key
            render()
        }
    }

    private fun setupAnchor() {
        (if (fromCompletion) binding.chipAnchorCompletion else binding.chipAnchorDue).isChecked = true
        binding.chipGroupAnchor.setOnCheckedStateChangeListener { _, checkedIds ->
            fromCompletion = binding.chipAnchorCompletion.id in checkedIds
            render()
        }
    }

    private fun newChip(text: String) = Chip(context).apply {
        id = View.generateViewId()
        this.text = text
        isCheckable = true
    }

    // ---------------------------------------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------------------------------------

    private fun currentInterval(): Int? =
        binding.etInterval.text.toString().toIntOrNull()?.takeIf { it in 1..RecurrenceRule.MAX_INTERVAL }

    private fun currentRule(): RecurrenceRule? {
        val interval = currentInterval() ?: return null
        return RecurrenceRule(
            frequency = frequency,
            interval = interval,
            weekdays = weekdays.toSet(),
            monthDay = monthDay,
            fromCompletion = fromCompletion
        ).normalized()
    }

    /** Pinta la visibilidad de las secciones, el resumen, las próximas fechas y los botones. */
    private fun render() {
        val interval = currentInterval()
        binding.groupWeekdays.isVisible = frequency == Frequency.WEEKLY && !fromCompletion
        binding.tvWeekdaysHint.isVisible = weekdays.isEmpty()
        binding.groupMonthDay.isVisible = frequency == Frequency.MONTHLY && !fromCompletion
        binding.tvAnchorHint.isVisible = fromCompletion
        binding.btnIntervalDecrease.isEnabled = interval != null && interval > 1
        binding.btnIntervalIncrease.isEnabled = interval == null || interval < RecurrenceRule.MAX_INTERVAL

        val rule = currentRule()
        binding.btnApply.isEnabled = rule != null
        if (rule == null) {
            binding.tvRecurrenceSummary.text =
                context.getString(R.string.recurrence_interval_error, RecurrenceRule.MAX_INTERVAL)
            binding.tvRecurrenceSummary.setTextColor(color(androidx.appcompat.R.attr.colorError))
            binding.tvRecurrenceUpcoming.isVisible = false
            return
        }
        binding.tvRecurrenceSummary.text = RecurrenceFormatter.describe(context, rule)
        binding.tvRecurrenceSummary.setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
        // Al contar desde que se completa, las fechas dependen de cuándo se haga: lo explica el aviso
        binding.tvRecurrenceUpcoming.isVisible = !rule.fromCompletion
        if (!rule.fromCompletion) {
            binding.tvRecurrenceUpcoming.text =
                context.getString(R.string.recurrence_upcoming, upcomingDates(rule).joinToString(" · "))
        }
    }

    private fun upcomingDates(rule: RecurrenceRule): List<String> {
        val first = if (rule.matches(anchorDate)) anchorDate else rule.next(anchorDate)
        val dates = generateSequence(first) { rule.next(it) }.take(UPCOMING_COUNT).toList()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val sameYear = dates.all { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.YEAR) == currentYear }
        val pattern = DateFormat.getBestDateTimePattern(locale, if (sameYear) "EEEdMMM" else "EEEdMMMy")
        val format = SimpleDateFormat(pattern, locale)
        return dates.map { format.format(Date(it)).lowercase(locale) }
    }

    private fun color(attr: Int): Int = MaterialColors.getColor(context, attr, 0)

    companion object {
        private const val UPCOMING_COUNT = 3
    }
}
