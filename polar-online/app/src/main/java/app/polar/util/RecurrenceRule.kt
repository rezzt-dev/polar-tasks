package app.polar.util

import java.util.Calendar

/**
 * Regla de repetición de una tarea, sin dependencias de Android para poder probarla en JVM.
 *
 * Se guarda en `Task.recurrence` como texto ([encode]/[parse]):
 * - Las reglas de siempre conservan su valor ("DAILY", "WEEKLY", "MONTHLY", "MON_WED",
 *   "FIRST_DAY_MONTH"), así que los datos y las copias anteriores siguen siendo válidos.
 * - El resto usa un subconjunto de RRULE (RFC 5545): `RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE`.
 *   `BYMONTHDAY=-1` es el último día del mes y la extensión `X-FROM=COMPLETION` cuenta el
 *   intervalo desde el día en que se completa la tarea en lugar de desde su fecha.
 *
 * @param weekdays días de la semana ([Calendar.MONDAY]...); solo en [Frequency.WEEKLY]. Vacío
 *   repite el mismo día de la semana que la fecha de la tarea.
 * @param monthDay día del mes (1..31, o [LAST_DAY_OF_MONTH]); solo en [Frequency.MONTHLY]. `null`
 *   repite el mismo día del mes que la fecha de la tarea.
 * @param fromCompletion la siguiente fecha es "[interval] unidades después de completarla"; en ese
 *   modo no se usan [weekdays] ni [monthDay].
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val fromCompletion: Boolean = false
) {

    enum class Frequency(val code: String, val calendarField: Int) {
        DAILY("DAILY", Calendar.DAY_OF_YEAR),
        WEEKLY("WEEKLY", Calendar.WEEK_OF_YEAR),
        MONTHLY("MONTHLY", Calendar.MONTH),
        YEARLY("YEARLY", Calendar.YEAR)
    }

    init {
        require(interval in 1..MAX_INTERVAL) { "interval out of range: $interval" }
        require(weekdays.all { it in Calendar.SUNDAY..Calendar.SATURDAY }) { "invalid weekday" }
        require(monthDay == null || monthDay == LAST_DAY_OF_MONTH || monthDay in 1..31) { "invalid month day" }
    }

    /** Regla tal y como se aplica: sin los campos que no corresponden a su frecuencia o modo. */
    fun normalized(): RecurrenceRule = copy(
        weekdays = if (frequency == Frequency.WEEKLY && !fromCompletion) weekdays else emptySet(),
        monthDay = if (frequency == Frequency.MONTHLY && !fromCompletion) monthDay else null
    )

    /** Valor para `Task.recurrence`: el de siempre si la regla equivale a uno de ellos. */
    fun encode(): String {
        val rule = normalized()
        LEGACY.entries.firstOrNull { it.value == rule }?.let { return it.key }
        val parts = mutableListOf("FREQ=${rule.frequency.code}")
        if (rule.interval != 1) parts += "INTERVAL=${rule.interval}"
        if (rule.weekdays.isNotEmpty()) {
            parts += "BYDAY=" + ISO_WEEK.filter { it in rule.weekdays }.joinToString(",") { DAY_CODES.getValue(it) }
        }
        rule.monthDay?.let { parts += "BYMONTHDAY=$it" }
        if (rule.fromCompletion) parts += "$FROM_KEY=$FROM_COMPLETION"
        return PREFIX + parts.joinToString(";")
    }

    /** Primera ocurrencia estrictamente posterior a [from], a la misma hora del día. */
    fun next(from: Long): Long {
        val rule = normalized()
        val calendar = Calendar.getInstance().apply { timeInMillis = from }
        when {
            rule.weekdays.isNotEmpty() -> calendar.toNextWeekday(rule.weekdays, rule.interval)
            rule.monthDay != null -> calendar.toNextMonthDay(rule.monthDay, rule.interval)
            else -> calendar.add(rule.frequency.calendarField, rule.interval)
        }
        return calendar.timeInMillis
    }

    /** Si [time] cae en un día que la regla permite (siempre, si no fija días concretos). */
    fun matches(time: Long): Boolean {
        val rule = normalized()
        val calendar = Calendar.getInstance().apply { timeInMillis = time }
        return when {
            rule.weekdays.isNotEmpty() -> calendar.get(Calendar.DAY_OF_WEEK) in rule.weekdays
            rule.monthDay != null -> calendar.get(Calendar.DAY_OF_MONTH) == calendar.resolveMonthDay(rule.monthDay)
            else -> true
        }
    }

    // Semanas de lunes a domingo (WKST=MO, el valor por defecto de RRULE): sin depender del
    // idioma, "cada 2 semanas el domingo y el lunes" cae siempre en las mismas semanas.
    private fun Calendar.toNextWeekday(days: Set<Int>, interval: Int) {
        val current = isoIndex(get(Calendar.DAY_OF_WEEK))
        val selected = days.map(::isoIndex).sorted()
        val laterThisWeek = selected.firstOrNull { it > current }
        val offset = if (laterThisWeek != null) laterThisWeek - current
        else (7 - current) + 7 * (interval - 1) + selected.first()
        add(Calendar.DAY_OF_YEAR, offset)
    }

    // Un día que aún no ha llegado este mes es la siguiente ocurrencia (alinea la primera fecha);
    // si ya pasó, se salta [interval] meses. Los días que el mes no tiene caen en su último día.
    private fun Calendar.toNextMonthDay(monthDay: Int, interval: Int) {
        val today = get(Calendar.DAY_OF_MONTH)
        if (resolveMonthDay(monthDay) <= today) {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, interval)
        }
        set(Calendar.DAY_OF_MONTH, resolveMonthDay(monthDay))
    }

    private fun Calendar.resolveMonthDay(monthDay: Int): Int {
        val last = getActualMaximum(Calendar.DAY_OF_MONTH)
        return if (monthDay == LAST_DAY_OF_MONTH) last else minOf(monthDay, last)
    }

    companion object {
        const val MAX_INTERVAL = 999
        const val LAST_DAY_OF_MONTH = -1

        private const val PREFIX = "RRULE:"
        private const val FROM_KEY = "X-FROM"
        private const val FROM_COMPLETION = "COMPLETION"

        /** Días de la semana de lunes a domingo. */
        val ISO_WEEK = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        private val DAY_CODES = mapOf(
            Calendar.MONDAY to "MO", Calendar.TUESDAY to "TU", Calendar.WEDNESDAY to "WE",
            Calendar.THURSDAY to "TH", Calendar.FRIDAY to "FR", Calendar.SATURDAY to "SA",
            Calendar.SUNDAY to "SU"
        )
        private val DAYS_BY_CODE = DAY_CODES.entries.associate { (day, code) -> code to day }

        private val LEGACY = mapOf(
            RecurrenceUtils.DAILY to RecurrenceRule(Frequency.DAILY),
            RecurrenceUtils.WEEKLY to RecurrenceRule(Frequency.WEEKLY),
            RecurrenceUtils.MONTHLY to RecurrenceRule(Frequency.MONTHLY),
            RecurrenceUtils.MON_WED to RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY)),
            RecurrenceUtils.FIRST_DAY_MONTH to RecurrenceRule(Frequency.MONTHLY, monthDay = 1)
        )

        private fun isoIndex(dayOfWeek: Int): Int = (dayOfWeek + 5) % 7

        /**
         * Regla guardada en `Task.recurrence`, o `null` si no se repite o el valor no se entiende
         * (una copia de una versión futura, por ejemplo): la tarea se trata como no recurrente en
         * lugar de fallar.
         */
        fun parse(value: String?): RecurrenceRule? {
            if (value.isNullOrBlank()) return null
            LEGACY[value]?.let { return it }
            if (!value.startsWith(PREFIX)) return null
            val fields = value.removePrefix(PREFIX).split(';')
                .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, v) -> key.trim().uppercase() to v.trim().uppercase() }
            val frequency = Frequency.entries.firstOrNull { it.code == fields["FREQ"] } ?: return null
            val interval = fields["INTERVAL"]?.let { it.toIntOrNull() ?: return null } ?: 1
            val weekdays = fields["BYDAY"]?.split(',')?.filter { it.isNotBlank() }
                ?.map { DAYS_BY_CODE[it] ?: return null }?.toSet() ?: emptySet()
            val monthDay = fields["BYMONTHDAY"]?.let { it.toIntOrNull() ?: return null }
            return runCatching {
                RecurrenceRule(
                    frequency = frequency,
                    interval = interval,
                    weekdays = weekdays,
                    monthDay = monthDay,
                    fromCompletion = fields[FROM_KEY] == FROM_COMPLETION
                ).normalized()
            }.getOrNull()
        }
    }
}
