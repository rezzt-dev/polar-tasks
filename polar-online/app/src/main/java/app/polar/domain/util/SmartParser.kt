package app.polar.domain.util

import app.polar.util.RecurrenceRule
import app.polar.util.RecurrenceUtils
import java.text.Normalizer
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class ParsedTask(
    val title: String,
    val dueDate: Long?,
    val tags: List<String>,
    val recurrence: String, // "NONE" o un valor de RecurrenceRule.encode() ("DAILY", "RRULE:FREQ=WEEKLY;INTERVAL=2"...)
    val priority: Int? = null, // 1: baja, 2: media, 3: alta; null si el texto no la indica
    val listId: Long? = null, // lista indicada con "@nombre" que existe en [SmartParser.parse]
    val unknownList: String? = null // "@nombre" que no coincide con ninguna lista (se queda en el título)
)

/**
 * Entiende lenguaje natural en el título de una tarea, en el idioma de la app (ver
 * [SmartParserLexicon]): etiquetas (`#casa`), lista (`@trabajo`), prioridad (`!`, `!!`, `!!!`,
 * `!alta`, "prioridad alta"), fechas (hoy, mañana, pasado mañana, "el viernes", "en 3 días",
 * "la próxima semana", "este fin de semana"), hora ("a las 17", "5:30pm") y repetición ("cada día",
 * "cada 2 semanas", "cada lunes", "los lunes y miércoles", "el primer día de cada mes").
 *
 * Cada expresión reconocida se quita del título. Sin dependencias de Android para probarlo en JVM.
 */
object SmartParser {

    // Límites de palabra Unicode: \b solo trata como letras las ASCII en la JVM ("mañana", "à").
    private const val START = """(?<![\p{L}\p{N}])"""
    private const val END = """(?![\p{L}\p{N}])"""

    private val tagRegex = Regex("""(?<!\S)#([\p{L}\p{N}_-]+)""")
    private val listRegex = Regex("""(?<!\S)@([\p{L}\p{N}_.-]+)""")
    private val prioritySymbolRegex = Regex("""(?<!\S)(!{1,3})(?!\S)""")
    private val whitespaceRegex = Regex("""\s+""")
    // Solo coma y punto: el francés deja espacio antes de ":" y ";"
    private val spaceBeforePunctuationRegex = Regex("""\s+([,.])(?=\s|$)""")
    private val edgePunctuationRegex = Regex("""^[\s,;:–-]+|[\s,;:–-]+$""")
    private val diacriticsRegex = Regex("""\p{Mn}+""")

    private val grammars = ConcurrentHashMap<String, Grammar>()

    /**
     * @param defaultDate fecha ya elegida para la tarea; se conserva si el texto solo indica la hora
     *   (o nada), y la sustituye una fecha escrita en el texto.
     * @param language idioma de la app ("es", "en", "fr", "de").
     * @param lists listas disponibles (id -> nombre) para resolver "@nombre".
     * @param now instante de referencia de las fechas relativas.
     */
    fun parse(
        input: String,
        defaultDate: Long? = null,
        defaultHour: Int = 7,
        defaultMinute: Int = 30,
        language: String = "es",
        lists: Map<Long, String> = emptyMap(),
        now: Long = System.currentTimeMillis()
    ): ParsedTask {
        val grammar = grammars.getOrPut(language.lowercase()) {
            Grammar(SmartParserLexicon.forLanguage(language))
        }
        val text = Remaining(input)
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, defaultHour)
            set(Calendar.MINUTE, defaultMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 1. Etiquetas, lista y prioridad (marcas con símbolo, iguales en todos los idiomas)
        val tags = text.takeAll(tagRegex).map { it.groupValues[1] }.distinct()
        val (listId, unknownList) = extractList(text, lists)
        val priority = extractPriority(text, grammar)

        // 2. Repetición; las que indican un día ("cada lunes") también dan la primera fecha
        var recurrence = "NONE"
        var date: Calendar? = null
        when {
            text.take(grammar.mondayAndWednesday) != null -> {
                recurrence = RecurrenceUtils.MON_WED
                date = nextWeekday(today, listOf(Calendar.MONDAY, Calendar.WEDNESDAY), includeToday = true)
            }
            text.take(grammar.firstDayOfMonth) != null -> {
                recurrence = RecurrenceUtils.FIRST_DAY_MONTH
                date = if (today.get(Calendar.DAY_OF_MONTH) == 1) copyOf(today) else firstDayOfNextMonth(today)
            }
            else -> {
                val everyWeekday = grammar.everyWeekday.firstNotNullOfOrNull { (day, regex) ->
                    text.take(regex)?.let { day }
                }
                val everyAmount = extractEveryAmount(text, grammar)
                when {
                    everyAmount != null -> {
                        // "cada 2 semanas los lunes": el día fija la regla semanal y la primera fecha
                        val rule = if (everyWeekday != null && everyAmount.frequency == RecurrenceRule.Frequency.WEEKLY) {
                            everyAmount.copy(weekdays = setOf(everyWeekday))
                        } else everyAmount
                        recurrence = rule.encode()
                        everyWeekday?.let { date = nextWeekday(today, listOf(it), includeToday = true) }
                    }
                    everyWeekday != null -> {
                        recurrence = RecurrenceUtils.WEEKLY
                        date = nextWeekday(today, listOf(everyWeekday), includeToday = true)
                    }
                    text.take(grammar.daily) != null -> recurrence = RecurrenceUtils.DAILY
                    text.take(grammar.weekly) != null -> recurrence = RecurrenceUtils.WEEKLY
                    text.take(grammar.monthly) != null -> recurrence = RecurrenceUtils.MONTHLY
                }
            }
        }

        // 3. Fecha escrita; tiene preferencia sobre la que da la repetición
        extractDate(text, grammar, today)?.let { date = it }

        // 4. Hora
        val time = extractTime(text, grammar)

        val dueDate = resolveDueDate(date, time, defaultDate, now)

        return ParsedTask(
            title = text.cleaned(),
            dueDate = dueDate,
            tags = tags,
            recurrence = recurrence,
            priority = priority,
            listId = listId,
            unknownList = unknownList
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Extracción
    // ---------------------------------------------------------------------------------------------

    /**
     * Primera "@nombre" que coincide con una lista, sin distinguir mayúsculas, tildes, espacios ni
     * guiones ("@listadecompra" -> "Lista de compra"); si no hay coincidencia exacta, vale un prefijo
     * de al menos 3 letras que solo encaje con una lista ("@comp" -> "Compras").
     */
    private fun extractList(text: Remaining, lists: Map<Long, String>): Pair<Long?, String?> {
        if (lists.isEmpty()) return null to null
        val candidates = lists.mapValues { normalize(it.value) }.filterValues { it.isNotEmpty() }
        var unknown: String? = null
        for (match in listRegex.findAll(text.value).toList()) {
            val query = normalize(match.groupValues[1])
            if (query.isEmpty()) continue
            val id = candidates.entries.firstOrNull { it.value == query }?.key
                ?: candidates.filterValues { query.length >= 3 && it.startsWith(query) }
                    .keys.singleOrNull()
            if (id != null) {
                text.remove(match)
                return id to null
            }
            // Mientras sea el principio de alguna lista se está escribiendo: no es un error todavía
            if (unknown == null && candidates.values.none { it.startsWith(query) }) {
                unknown = match.groupValues[1]
            }
        }
        return null to unknown
    }

    /** "cada 2 semanas", "cada tres días"... */
    private fun extractEveryAmount(text: Remaining, grammar: Grammar): RecurrenceRule? {
        val match = grammar.everyAmount.find(text.value) ?: return null
        val amount = amountOf(match.groupValues[1], grammar)
            ?.takeIf { it in 1..RecurrenceRule.MAX_INTERVAL } ?: return null
        val frequency = when {
            match.groupValues[2].isNotEmpty() -> RecurrenceRule.Frequency.DAILY
            match.groupValues[3].isNotEmpty() -> RecurrenceRule.Frequency.WEEKLY
            match.groupValues[4].isNotEmpty() -> RecurrenceRule.Frequency.MONTHLY
            else -> RecurrenceRule.Frequency.YEARLY
        }
        text.remove(match)
        return RecurrenceRule(frequency, interval = amount)
    }

    private fun amountOf(value: String, grammar: Grammar): Int? =
        value.toIntOrNull() ?: grammar.numberWords[value.lowercase()]

    private fun extractPriority(text: Remaining, grammar: Grammar): Int? {
        text.take(prioritySymbolRegex)?.let { return it.groupValues[1].length }
        grammar.priorityWords.forEach { (level, regex) -> if (text.take(regex) != null) return level }
        grammar.priorityPhrases.forEach { (level, regex) -> if (text.take(regex) != null) return level }
        return null
    }

    private fun extractDate(text: Remaining, grammar: Grammar, today: Calendar): Calendar? {
        // "pasado mañana" antes que "mañana"
        if (text.take(grammar.dayAfterTomorrow) != null) return copyOf(today).addDays(2)
        if (text.take(grammar.today) != null) return copyOf(today)
        if (text.take(grammar.tomorrow) != null) return copyOf(today).addDays(1)

        text.take(grammar.inAmount)?.let { match ->
            val amount = amountOf(match.groupValues[1], grammar)
            if (amount != null) {
                val field = when {
                    match.groupValues[2].isNotEmpty() -> Calendar.DAY_OF_YEAR
                    match.groupValues[3].isNotEmpty() -> Calendar.WEEK_OF_YEAR
                    else -> Calendar.MONTH
                }
                return copyOf(today).apply { add(field, amount) }
            }
        }

        if (text.take(grammar.nextWeek) != null) {
            return nextWeekday(today, listOf(Calendar.MONDAY), includeToday = false)
        }
        if (text.take(grammar.nextMonth) != null) return firstDayOfNextMonth(today)
        if (text.take(grammar.weekend) != null) {
            // En pleno fin de semana, "este fin de semana" es hoy
            return if (today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) copyOf(today)
            else nextWeekday(today, listOf(Calendar.SATURDAY), includeToday = true)
        }

        // "el viernes" dicho un viernes es el de la semana que viene
        grammar.weekday.forEach { (day, regex) ->
            if (text.take(regex) != null) return nextWeekday(today, listOf(day), includeToday = false)
        }
        return null
    }

    /**
     * Primera hora válida del texto. Un número suelto solo cuenta como hora si lleva minutos
     * ("17:00"), sufijo ("5pm", "17h", "17 uhr") o preposición ("a las 17"): así "comprar 2 panes"
     * no se convierte en las 2:00.
     */
    private fun extractTime(text: Remaining, grammar: Grammar): Pair<Int, Int>? {
        for (match in grammar.time.findAll(text.value).toList()) {
            val hasPrefix = match.groupValues[1].isNotEmpty()
            val minuteStr = match.groupValues[3]
            val suffix = match.groupValues[4].lowercase()
            if (!hasPrefix && minuteStr.isEmpty() && suffix.isEmpty()) continue

            var hour = match.groupValues[2].toInt()
            val minute = minuteStr.toIntOrNull() ?: 0
            val isPm = suffix.startsWith("p")
            val isAm = suffix.startsWith("a")
            if (minute > 59) continue
            if (isPm || isAm) {
                if (hour !in 1..12) continue
                if (isPm && hour < 12) hour += 12
                if (isAm && hour == 12) hour = 0
            } else if (hour > 23) {
                continue
            }
            text.remove(match)
            return hour to minute
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Fechas
    // ---------------------------------------------------------------------------------------------

    private fun resolveDueDate(date: Calendar?, time: Pair<Int, Int>?, defaultDate: Long?, now: Long): Long? {
        if (date != null) {
            time?.let { (hour, minute) -> date.setClock(hour, minute) }
            return date.timeInMillis
        }
        if (time == null) return defaultDate

        val (hour, minute) = time
        val base = Calendar.getInstance().apply { timeInMillis = defaultDate ?: now }
        base.setClock(hour, minute)
        // Solo la hora y sin fecha previa: si esa hora ya ha pasado hoy, es la de mañana
        if (defaultDate == null && base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 1)
        return base.timeInMillis
    }

    /** Siguiente día de [days] a partir de [from] (hoy incluido si [includeToday]). */
    private fun nextWeekday(from: Calendar, days: List<Int>, includeToday: Boolean): Calendar {
        val result = copyOf(from)
        if (!includeToday) result.add(Calendar.DAY_OF_YEAR, 1)
        while (result.get(Calendar.DAY_OF_WEEK) !in days) result.add(Calendar.DAY_OF_YEAR, 1)
        return result
    }

    private fun firstDayOfNextMonth(from: Calendar): Calendar = copyOf(from).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, 1)
    }

    private fun copyOf(calendar: Calendar): Calendar = calendar.clone() as Calendar

    private fun Calendar.addDays(days: Int): Calendar = apply { add(Calendar.DAY_OF_YEAR, days) }

    private fun Calendar.setClock(hour: Int, minute: Int) {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /** Clave de comparación de nombres de lista: minúsculas, sin tildes y solo letras y números. */
    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .lowercase()
            .filter { it.isLetterOrDigit() }

    // ---------------------------------------------------------------------------------------------
    // Soporte
    // ---------------------------------------------------------------------------------------------

    /** Texto pendiente de analizar: cada expresión reconocida se sustituye por un espacio. */
    private class Remaining(var value: String) {

        fun take(regex: Regex): MatchResult? = regex.find(value)?.also { remove(it) }

        fun takeAll(regex: Regex): List<MatchResult> {
            val matches = regex.findAll(value).toList()
            // De atrás hacia delante para que los rangos sigan siendo válidos
            matches.asReversed().forEach { remove(it) }
            return matches
        }

        fun remove(match: MatchResult) {
            value = value.replaceRange(match.range, " ")
        }

        fun cleaned(): String = value
            .replace(whitespaceRegex, " ")
            .replace(spaceBeforePunctuationRegex, "$1")
            .replace(edgePunctuationRegex, "")
    }

    /** Expresiones compiladas de un idioma. */
    private class Grammar(lexicon: SmartParserLexicon) {
        private val weekdayConstants = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        val today = word(lexicon.today)
        val tomorrow = word(lexicon.tomorrow)
        val dayAfterTomorrow = word(lexicon.dayAfterTomorrow)
        val weekday = perWeekday(lexicon, lexicon.weekday)
        val everyWeekday = perWeekday(lexicon, lexicon.everyWeekday)
        val mondayAndWednesday = word(lexicon.mondayAndWednesday)
        val firstDayOfMonth = word(lexicon.firstDayOfMonth)
        val daily = word(lexicon.daily)
        val weekly = word(lexicon.weekly)
        val monthly = word(lexicon.monthly)
        val numberWords = lexicon.numberWords
        val nextWeek = word(lexicon.nextWeek)
        val nextMonth = word(lexicon.nextMonth)
        val weekend = word(lexicon.weekend)

        // Grupos: 1 cantidad, 2 días, 3 semanas, 4 meses
        val inAmount = word(
            "${lexicon.inAmount}\\s+(\\d{1,3}|${alternatives(lexicon.numberWords.keys)})\\s+" +
                "(?:(${lexicon.dayUnit})|(${lexicon.weekUnit})|(${lexicon.monthUnit}))"
        )

        // Grupos: 1 cantidad, 2 días, 3 semanas, 4 meses, 5 años ("cada 2 semanas")
        val everyAmount = word(
            "(?:${lexicon.everyAmount})\\s+(\\d{1,3}|${alternatives(lexicon.numberWords.keys)})\\s+" +
                "(?:(${lexicon.dayUnit})|(${lexicon.weekUnit})|(${lexicon.monthUnit})|(${lexicon.yearUnit}))"
        )

        // Grupos: 1 preposición, 2 hora, 3 minutos, 4 sufijo ("17:30", "17.30", "17h30", "5pm", "17 uhr")
        val time = word(
            "(?:(${lexicon.timePrefix})\\s+)?(\\d{1,2})(?:(?:[:.]|h)(\\d{2}))?" +
                "\\s*(am|a\\.m\\.|pm|p\\.m\\.|${lexicon.timeSuffix})?"
        )

        val priorityWords = lexicon.priorityWords.mapValues { (_, words) ->
            Regex("""(?<!\S)!(?:$words)(?!\S)""", RegexOption.IGNORE_CASE)
        }
        val priorityPhrases = lexicon.priorityPhrases.mapValues { (_, phrase) -> word(phrase) }

        private fun perWeekday(lexicon: SmartParserLexicon, template: String): List<Pair<Int, Regex>> =
            lexicon.weekdays.mapIndexed { index, name ->
                weekdayConstants[index] to word(template.replace("{d}", "(?:$name)"))
            }

        // Las alternativas largas primero para que "una" no se quede en "un"
        private fun alternatives(words: Collection<String>): String =
            words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        private fun word(pattern: String) = Regex("$START(?:$pattern)$END", RegexOption.IGNORE_CASE)
    }
}
