package app.polar.util

import android.content.Context
import app.polar.R
import app.polar.util.RecurrenceRule.Frequency
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * Textos de la interfaz para una repetición: "cada 2 semanas: lun, mié", "3 días después de
 * completarla"... Las reglas de siempre conservan su texto ("cada lunes y miércoles").
 */
object RecurrenceFormatter {

    /** Resumen de un valor de `Task.recurrence`; "no se repite" si no es recurrente. */
    fun describe(context: Context, recurrence: String): String {
        val rule = RecurrenceRule.parse(recurrence)
            ?: return context.getString(R.string.recurrence_summary_none)
        return describe(context, rule)
    }

    fun describe(context: Context, rule: RecurrenceRule): String {
        val normalized = rule.normalized()
        legacyText(context, normalized.encode())?.let { return it }

        val resources = context.resources
        val interval = normalized.interval
        if (normalized.fromCompletion) {
            return resources.getQuantityString(afterRes(normalized.frequency), interval, interval)
        }
        val base = if (interval == 1) context.getString(everyOneRes(normalized.frequency))
        else resources.getQuantityString(everyRes(normalized.frequency), interval, interval)
        val locale = localeOf(context)
        val detail = when {
            normalized.weekdays.isNotEmpty() -> weekdaysInLocaleOrder(locale)
                .filter { it in normalized.weekdays }
                .joinToString(", ") { shortWeekdayName(it, locale) }
            normalized.monthDay != null -> monthDayText(context, normalized.monthDay)
            else -> null
        } ?: return base
        return context.getString(R.string.recurrence_summary_with_detail, base, detail)
    }

    /** "el primer día", "el último día" o "el día 15". */
    fun monthDayText(context: Context, monthDay: Int): String = when (monthDay) {
        1 -> context.getString(R.string.recurrence_month_day_first)
        RecurrenceRule.LAST_DAY_OF_MONTH -> context.getString(R.string.recurrence_month_day_last)
        else -> context.getString(R.string.recurrence_month_day_number, monthDay)
    }

    /** Días de la semana empezando por el primero del idioma (lunes en castellano, domingo en EE. UU.). */
    fun weekdaysInLocaleOrder(locale: Locale): List<Int> {
        val first = Calendar.getInstance(locale).firstDayOfWeek
        return (0 until 7).map { (first - 1 + it) % 7 + 1 }
    }

    fun shortWeekdayName(day: Int, locale: Locale): String =
        DateFormatSymbols.getInstance(locale).shortWeekdays[day].lowercase(locale)

    fun localeOf(context: Context): Locale = context.resources.configuration.locales[0]

    /** Nombre de la unidad en el selector ("semanas"). */
    fun unitRes(frequency: Frequency): Int = when (frequency) {
        Frequency.DAILY -> R.string.recurrence_unit_day
        Frequency.WEEKLY -> R.string.recurrence_unit_week
        Frequency.MONTHLY -> R.string.recurrence_unit_month
        Frequency.YEARLY -> R.string.recurrence_unit_year
    }

    private fun everyOneRes(frequency: Frequency): Int = when (frequency) {
        Frequency.DAILY -> R.string.recurrence_summary_daily
        Frequency.WEEKLY -> R.string.recurrence_summary_weekly
        Frequency.MONTHLY -> R.string.recurrence_summary_monthly
        Frequency.YEARLY -> R.string.recurrence_summary_yearly
    }

    private fun everyRes(frequency: Frequency): Int = when (frequency) {
        Frequency.DAILY -> R.plurals.recurrence_every_day
        Frequency.WEEKLY -> R.plurals.recurrence_every_week
        Frequency.MONTHLY -> R.plurals.recurrence_every_month
        Frequency.YEARLY -> R.plurals.recurrence_every_year
    }

    private fun afterRes(frequency: Frequency): Int = when (frequency) {
        Frequency.DAILY -> R.plurals.recurrence_after_day
        Frequency.WEEKLY -> R.plurals.recurrence_after_week
        Frequency.MONTHLY -> R.plurals.recurrence_after_month
        Frequency.YEARLY -> R.plurals.recurrence_after_year
    }

    private fun legacyText(context: Context, recurrence: String): String? = when (recurrence) {
        RecurrenceUtils.DAILY -> context.getString(R.string.recurrence_summary_daily)
        RecurrenceUtils.WEEKLY -> context.getString(R.string.recurrence_summary_weekly)
        RecurrenceUtils.MONTHLY -> context.getString(R.string.recurrence_summary_monthly)
        RecurrenceUtils.MON_WED -> context.getString(R.string.recurrence_summary_mon_wed)
        RecurrenceUtils.FIRST_DAY_MONTH -> context.getString(R.string.recurrence_summary_first_day_month)
        else -> null
    }
}
