package app.polar.domain.util

import java.util.Calendar
import java.util.regex.Pattern

data class ParsedTask(
    val title: String,
    val dueDate: Long?,
    val tags: List<String>,
    val recurrence: String // "NONE", "DAILY", "WEEKLY", "MONTHLY"
)

object SmartParser {

    private val tagRegex = Regex("""(?:^|\s)#(\w+)""")
    
    // Recurrence regexes
    private val dailyRegex = Regex("""(?i)\b(cada d[ií]a|diar[ií]amente|todos los d[ií]as)\b""")
    private val weeklyRegex = Regex("""(?i)\b(cada semana|semanalmente|todas las semanas|cada (lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo))\b""")
    private val monthlyRegex = Regex("""(?i)\b(cada mes|mensualmente|todos los meses)\b""")

    // Date regexes
    private val todayRegex = Regex("""(?i)\b(hoy|esta noche)\b""")
    private val tomorrowRegex = Regex("""(?i)\b(ma[nñ]ana)\b""")
    private val afterTomorrowRegex = Regex("""(?i)\b(pasado ma[nñ]ana)\b""")
    
    // Time regexes (e.g., 17:00, 17.00, 5:30pm, 5 pm, a las 17)
    private val timeRegex = Regex("""(?i)\b(?:a las?\s+)?(\d{1,2})[:.]?(\d{2})?\s*(am|a\.m\.|pm|p\.m\.)?\b""")

    fun parse(input: String, defaultDate: Long? = null, defaultHour: Int = 7, defaultMinute: Int = 30): ParsedTask {
        var cleanTitle = input
        val extractedTags = mutableListOf<String>()
        var extractedRecurrence = "NONE"
        var parsedCalendar: Calendar? = if (defaultDate != null) {
            Calendar.getInstance().apply { timeInMillis = defaultDate }
        } else null

        // 1. Extract Tags
        tagRegex.findAll(input).forEach { match ->
            extractedTags.add(match.groupValues[1])
            cleanTitle = cleanTitle.replace(match.value, "")
        }

        // 2. Extract Recurrence
        if (dailyRegex.containsMatchIn(input)) {
            extractedRecurrence = "DAILY"
            cleanTitle = cleanTitle.replace(dailyRegex, "")
        } else if (weeklyRegex.containsMatchIn(input)) {
            extractedRecurrence = "WEEKLY"
            cleanTitle = cleanTitle.replace(weeklyRegex, "")
        } else if (monthlyRegex.containsMatchIn(input)) {
            extractedRecurrence = "MONTHLY"
            cleanTitle = cleanTitle.replace(monthlyRegex, "")
        }

        // 3. Extract Dates
        var dateFound = false
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, defaultHour)
        cal.set(Calendar.MINUTE, defaultMinute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (todayRegex.containsMatchIn(input)) {
            parsedCalendar = parsedCalendar ?: cal.clone() as Calendar
            cleanTitle = cleanTitle.replace(todayRegex, "")
            dateFound = true
        } else if (tomorrowRegex.containsMatchIn(input)) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            parsedCalendar = parsedCalendar ?: cal.clone() as Calendar
            cleanTitle = cleanTitle.replace(tomorrowRegex, "")
            dateFound = true
        } else if (afterTomorrowRegex.containsMatchIn(input)) {
            cal.add(Calendar.DAY_OF_YEAR, 2)
            parsedCalendar = parsedCalendar ?: cal.clone() as Calendar
            cleanTitle = cleanTitle.replace(afterTomorrowRegex, "")
            dateFound = true
        }

        // 4. Extract Times
        val timeMatch = timeRegex.find(input)
        if (timeMatch != null) {
            val hourStr = timeMatch.groupValues[1]
            val minuteStr = timeMatch.groupValues[2]
            val amPmStr = timeMatch.groupValues[3].lowercase()

            var hour = hourStr.toIntOrNull() ?: 0
            val minute = minuteStr.toIntOrNull() ?: 0

            // Exclude single digit matches that don't have am/pm or minutes, 
            // to avoid catching random numbers like "Comprar 2 panes"
            val isLikelyTime = minuteStr.isNotEmpty() || amPmStr.isNotEmpty() || input.contains(Regex("""(?i)a las?\s+$hourStr"""))
            
            if (isLikelyTime) {
                if (amPmStr.contains("pm") && hour < 12) {
                    hour += 12
                } else if (amPmStr.contains("am") && hour == 12) {
                    hour = 0
                }

                if (parsedCalendar == null) {
                    parsedCalendar = Calendar.getInstance()
                }
                
                parsedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                parsedCalendar.set(Calendar.MINUTE, minute)
                parsedCalendar.set(Calendar.SECOND, 0)

                cleanTitle = cleanTitle.replace(timeMatch.value, "")
            }
        }

        return ParsedTask(
            title = cleanTitle.trim().replace(Regex("""\s+"""), " "), // Clear double spaces left behind
            dueDate = parsedCalendar?.timeInMillis,
            tags = extractedTags.distinct(),
            recurrence = extractedRecurrence
        )
    }
}
