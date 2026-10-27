package app.polar.domain.util

/**
 * Vocabulario del [SmartParser] para un idioma. Cada campo es un fragmento de expresión regular
 * (sin límites de palabra: los añade el parser) que se evalúa sin distinguir mayúsculas.
 *
 * En [weekday] y [everyWeekday], `{d}` se sustituye por el nombre de cada día de [weekdays].
 * Las variantes sin tilde se aceptan para quien escribe sin acentos.
 */
internal class SmartParserLexicon(
  val today: String,
  val tomorrow: String,
  val dayAfterTomorrow: String,
  /** De lunes a domingo. */
  val weekdays: List<String>,
  val weekday: String,
  val everyWeekday: String,
  val mondayAndWednesday: String,
  val firstDayOfMonth: String,
  val daily: String,
  val weekly: String,
  val monthly: String,
  /** Inicio de "cada 2 semanas"; la cantidad y la unidad las añade el parser. */
  val everyAmount: String,
  /** Preposición de "en 3 días"; la cantidad y la unidad las añade el parser. */
  val inAmount: String,
  val dayUnit: String,
  val weekUnit: String,
  val monthUnit: String,
  val yearUnit: String,
  val numberWords: Map<String, Int>,
  val nextWeek: String,
  val nextMonth: String,
  val weekend: String,
  val timePrefix: String,
  val timeSuffix: String,
  /** Palabras tras "!" ("!alta"), por nivel de prioridad (1 baja, 2 media, 3 alta). */
  val priorityWords: Map<Int, String>,
  /** Frases completas ("prioridad alta"), por nivel de prioridad. */
  val priorityPhrases: Map<Int, String>
) {

  companion object {

    private val SPANISH = SmartParserLexicon(
      today = """(?:para\s+)?(?:hoy|esta\s+noche|esta\s+tarde)""",
      // "por la mañana" y "esta mañana" hablan de la franja del día, no de mañana
      tomorrow = """(?:para\s+)?(?<!la\s)(?<!esta\s)ma[nñ]ana""",
      dayAfterTomorrow = """(?:para\s+)?pasado\s+ma[nñ]ana""",
      weekdays = listOf("lunes", "martes", "mi[eé]rcoles", "jueves", "viernes", "s[aá]bado", "domingo"),
      weekday = """(?:para\s+)?(?:el\s+|este\s+)?(?:pr[oó]ximo\s+)?{d}(?:\s+que\s+viene|\s+pr[oó]ximo)?""",
      everyWeekday = """(?:cada|todos\s+los|los)\s+{d}s?""",
      mondayAndWednesday = """(?:cada|todos\s+los|los)\s+lunes\s+y\s+(?:los\s+)?mi[eé]rcoles""",
      firstDayOfMonth = """(?:el\s+)?(?:primer\s+d[ií]a|d[ií]a\s+(?:1|uno))\s+de\s+cada\s+mes|cada\s+(?:d[ií]a\s+)?(?:1|uno|primero)\s+de\s+mes""",
      daily = """cada\s+d[ií]a|todos\s+los\s+d[ií]as|diariamente|a\s+diario""",
      weekly = """cada\s+semana|todas\s+las\s+semanas|semanalmente""",
      monthly = """cada\s+mes|todos\s+los\s+meses|mensualmente""",
      everyAmount = """cada""",
      inAmount = """(?:en|dentro\s+de)""",
      dayUnit = """d[ií]as?""",
      weekUnit = """semanas?""",
      monthUnit = """mes(?:es)?""",
      yearUnit = """a[nñ]os?""",
      numberWords = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "quince" to 15
      ),
      nextWeek = """(?:para\s+)?(?:la\s+)?(?:pr[oó]xima\s+semana|semana\s+que\s+viene|semana\s+pr[oó]xima)""",
      nextMonth = """(?:para\s+)?(?:el\s+)?(?:pr[oó]ximo\s+mes|mes\s+que\s+viene)""",
      weekend = """(?:para\s+)?(?:este|el)\s+fin\s+de\s+semana""",
      timePrefix = """a\s+las?|sobre\s+las?|hacia\s+las?""",
      timeSuffix = """h|hrs?|horas?""",
      priorityWords = mapOf(3 to "alta|urgente", 2 to "media", 1 to "baja"),
      priorityPhrases = mapOf(
        3 to """prioridad\s+alta|alta\s+prioridad""",
        2 to """prioridad\s+media""",
        1 to """prioridad\s+baja|baja\s+prioridad"""
      )
    )

    private val ENGLISH = SmartParserLexicon(
      today = """(?:for\s+)?(?:today|tonight|this\s+(?:evening|afternoon))""",
      tomorrow = """(?:for\s+|by\s+)?(?:tomorrow|tmrw)""",
      dayAfterTomorrow = """(?:the\s+)?day\s+after\s+tomorrow""",
      weekdays = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"),
      weekday = """(?:on\s+|by\s+|for\s+)?(?:this\s+(?:coming\s+)?|next\s+)?{d}""",
      everyWeekday = """(?:every|each)\s+{d}|(?:on\s+)?{d}s""",
      mondayAndWednesday = """(?:every|each)\s+monday\s+and\s+(?:every\s+)?wednesday|(?:on\s+)?mondays\s+and\s+wednesdays""",
      firstDayOfMonth = """(?:on\s+)?(?:the\s+)?(?:first|1st)(?:\s+day)?\s+of\s+(?:every|each)\s+month|(?:every|each)\s+(?:first|1st)\s+of\s+the\s+month""",
      // "daily", "weekly" y "monthly" sueltos solo al final: en medio suelen ser parte del
      // título ("weekly report")
      daily = """every\s*day|each\s+day|daily(?=\s*$)""",
      weekly = """every\s+week|each\s+week|weekly(?=\s*$)""",
      monthly = """every\s+month|each\s+month|monthly(?=\s*$)""",
      everyAmount = """every""",
      inAmount = """in""",
      dayUnit = """days?""",
      weekUnit = """weeks?""",
      monthUnit = """months?""",
      yearUnit = """years?""",
      numberWords = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
      ),
      nextWeek = """(?:for\s+|by\s+)?next\s+week""",
      nextMonth = """(?:for\s+|by\s+)?next\s+month""",
      weekend = """(?:this|next|on\s+the|at\s+the|over\s+the)\s+weekend""",
      timePrefix = """at|around""",
      timeSuffix = """o'?clock|h""",
      priorityWords = mapOf(3 to "high|urgent", 2 to "medium", 1 to "low"),
      priorityPhrases = mapOf(
        3 to """high\s+priority|priority\s+high""",
        2 to """medium\s+priority|priority\s+medium""",
        1 to """low\s+priority|priority\s+low"""
      )
    )

    private val FRENCH = SmartParserLexicon(
      today = """(?:pour\s+)?(?:aujourd['’]hui|ce\s+soir|cet\s+apr[eè]s-midi)""",
      tomorrow = """(?:pour\s+)?demain""",
      dayAfterTomorrow = """(?:pour\s+)?apr[eè]s[-\s]demain""",
      weekdays = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"),
      weekday = """(?:pour\s+)?(?:le\s+|ce\s+)?{d}(?:\s+prochain)?""",
      everyWeekday = """(?:chaque|tous\s+les)\s+{d}s?""",
      mondayAndWednesday = """(?:chaque|tous\s+les)\s+lundis?\s+et\s+(?:les\s+)?mercredis?""",
      firstDayOfMonth = """(?:le\s+)?(?:premier|1er)(?:\s+jour)?\s+de\s+chaque\s+mois|(?:chaque|tous\s+les)\s+(?:premier|1er)s?\s+du\s+mois""",
      daily = """chaque\s+jour|tous\s+les\s+jours|quotidiennement""",
      weekly = """chaque\s+semaine|toutes\s+les\s+semaines|hebdomadairement""",
      monthly = """chaque\s+mois|tous\s+les\s+mois|mensuellement""",
      everyAmount = """tous\s+les|toutes\s+les""",
      inAmount = """dans""",
      dayUnit = """jours?""",
      weekUnit = """semaines?""",
      monthUnit = """mois""",
      yearUnit = """ans|ann[ée]es""",
      numberWords = mapOf(
        "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5, "six" to 6,
        "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10, "quinze" to 15
      ),
      nextWeek = """(?:pour\s+)?la\s+semaine\s+prochaine""",
      nextMonth = """(?:pour\s+)?le\s+mois\s+prochain""",
      weekend = """(?:pour\s+)?(?:ce|le)\s+week-?end""",
      timePrefix = """à|vers""",
      timeSuffix = """h|heures?""",
      priorityWords = mapOf(3 to "haute|urgente?", 2 to "moyenne", 1 to "basse"),
      priorityPhrases = mapOf(
        3 to """priorit[ée]\s+haute|haute\s+priorit[ée]""",
        2 to """priorit[ée]\s+moyenne|moyenne\s+priorit[ée]""",
        1 to """priorit[ée]\s+basse|basse\s+priorit[ée]"""
      )
    )

    private val GERMAN = SmartParserLexicon(
      today = """(?:f[üu]r\s+)?heute(?:\s+(?:abend|nachmittag|fr[üu]h|morgen))?""",
      // "am morgen" es "por la mañana", no "mañana"
      tomorrow = """(?:f[üu]r\s+|bis\s+)?(?<!am\s)morgen(?:\s+fr[üu]h)?""",
      dayAfterTomorrow = """(?:f[üu]r\s+|bis\s+)?(?:[üu]bermorgen|uebermorgen)""",
      weekdays = listOf("montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag"),
      weekday = """(?:f[üu]r\s+|bis\s+)?(?:am\s+)?(?:diesen\s+|n[äa]chsten\s+|kommenden\s+)?{d}""",
      everyWeekday = """jeden\s+{d}|{d}s""",
      mondayAndWednesday = """jeden\s+montag\s+und\s+(?:jeden\s+)?mittwoch|montags\s+und\s+mittwochs""",
      firstDayOfMonth = """(?:am\s+)?(?:ersten|1\.)(?:\s+tag)?\s+(?:jedes|eines\s+jeden)\s+monats|jeden\s+(?:ersten|1\.)(?:\s+tag)?\s+(?:des|im)\s+monats?""",
      daily = """jeden\s+tag|t[äa]glich|taeglich""",
      weekly = """jede\s+woche|w[öo]chentlich|woechentlich""",
      monthly = """jeden\s+monat|monatlich""",
      everyAmount = """alle""",
      inAmount = """in""",
      dayUnit = """tag(?:e|en)?""",
      weekUnit = """wochen?""",
      monthUnit = """monat(?:e|en)?""",
      yearUnit = """jahr(?:e|en)?""",
      numberWords = mapOf(
        "ein" to 1, "einem" to 1, "einer" to 1, "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4,
        "fünf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "vierzehn" to 14
      ),
      nextWeek = """(?:f[üu]r\s+|bis\s+)?(?:in\s+der\s+)?(?:n[äa]chsten?|kommenden?)\s+woche""",
      nextMonth = """(?:f[üu]r\s+|bis\s+)?(?:im\s+)?(?:n[äa]chste[nm]?|kommende[nm]?)\s+monat""",
      weekend = """(?:am|dieses|diesem|n[äa]chstes)\s+wochenende""",
      timePrefix = """um|gegen""",
      timeSuffix = """uhr|h""",
      priorityWords = mapOf(3 to "hoch|dringend", 2 to "mittel", 1 to "niedrig"),
      priorityPhrases = mapOf(
        3 to """hohe[rn]?\s+priorit[äa]t|priorit[äa]t\s+hoch""",
        2 to """mittlere[rn]?\s+priorit[äa]t|priorit[äa]t\s+mittel""",
        1 to """niedrige[rn]?\s+priorit[äa]t|priorit[äa]t\s+niedrig"""
      )
    )

    /**
     * Léxico del idioma de la app ([java.util.Locale.getLanguage], p. ej. "es" o "en"); los dos
     * ingleses comparten léxico. Un idioma sin léxico usa el castellano, idioma por defecto.
     */
    fun forLanguage(language: String): SmartParserLexicon = when (language.lowercase()) {
      "en" -> ENGLISH
      "fr" -> FRENCH
      "de" -> GERMAN
      else -> SPANISH
    }
  }
}
