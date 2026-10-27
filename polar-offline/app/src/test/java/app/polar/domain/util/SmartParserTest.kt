package app.polar.domain.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class SmartParserTest {

    @Test
    fun testParse_TagsOnly() {
        val input = "Comprar pan #compras #urgente"
        val result = SmartParser.parse(input)

        assertEquals("Comprar pan", result.title)
        assertEquals(listOf("compras", "urgente"), result.tags)
        assertEquals("NONE", result.recurrence)
        assertNull(result.dueDate)
    }

    @Test
    fun testParse_DateRelativeToday() {
        val input = "Enviar correo hoy #trabajo"
        val result = SmartParser.parse(input, defaultHour = 0, defaultMinute = 0)

        assertEquals("Enviar correo", result.title)
        assertEquals(listOf("trabajo"), result.tags)

        val expectedCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedCal.timeInMillis, result.dueDate)
    }

    @Test
    fun testParse_DateRelativeTomorrow() {
        // "Mañana" should be +1 day
        val input = "Lavar coche mañana"
        val result = SmartParser.parse(input, defaultHour = 0, defaultMinute = 0)

        assertEquals("Lavar coche", result.title)

        val expectedCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedCal.timeInMillis, result.dueDate)
    }

    @Test
    fun testParse_TimeExtraction() {
        val input = "Reunión de equipo a las 17:00"
        val result = SmartParser.parse(input)

        assertEquals("Reunión de equipo", result.title)

        val resultCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(17, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
    }

    @Test
    fun testParse_TimeExtractionAmPm() {
        val input = "Tomar pastilla a las 5:30pm"
        val result = SmartParser.parse(input)

        assertEquals("Tomar pastilla", result.title)

        val resultCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }
        assertEquals(17, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun testParse_RecurrenceWeekly() {
        val input = "Hacer ejercicio cada semana"
        val result = SmartParser.parse(input)

        assertEquals("Hacer ejercicio", result.title)
        assertEquals("WEEKLY", result.recurrence)
    }

    @Test
    fun testParse_EverythingAtOnce() {
        val input = "Comprar leche mañana a las 19:15 #supermercado cada semana"
        val result = SmartParser.parse(input)

        assertEquals("Comprar leche", result.title)
        assertEquals(listOf("supermercado"), result.tags)
        assertEquals("WEEKLY", result.recurrence)

        val resultCal = Calendar.getInstance().apply { timeInMillis = result.dueDate!! }

        val expectedCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        assertEquals(expectedCal.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(19, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, resultCal.get(Calendar.MINUTE))
    }

    // ---------------------------------------------------------------------------------------------
    // Fechas relativas y días de la semana, con un "ahora" fijo: martes 27/10/2026 a las 10:00
    // ---------------------------------------------------------------------------------------------

    private val now = at(2026, Calendar.OCTOBER, 27, 10, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 7, minute: Int = 30): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun parse(input: String, language: String = "es", lists: Map<Long, String> = emptyMap(), defaultDate: Long? = null) =
        SmartParser.parse(input, defaultDate = defaultDate, language = language, lists = lists, now = now)

    @Test
    fun testParse_Weekday_NextOccurrence() {
        val result = parse("llamar a ana el viernes")
        assertEquals("llamar a ana", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 30), result.dueDate)
    }

    @Test
    fun testParse_Weekday_SameDayMeansNextWeek() {
        // hoy es martes: "el martes" es el de la semana que viene
        assertEquals(at(2026, Calendar.NOVEMBER, 3), parse("revisar informe el martes").dueDate)
    }

    @Test
    fun testParse_Weekday_WithoutAccentAndPrefix() {
        val result = parse("pagar alquiler para el proximo miercoles")
        assertEquals("pagar alquiler", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 28), result.dueDate)
    }

    @Test
    fun testParse_Weekday_WithTime() {
        val result = parse("dentista el jueves a las 9:15")
        assertEquals("dentista", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 29, 9, 15), result.dueDate)
    }

    @Test
    fun testParse_InDays() {
        val result = parse("renovar dni en 3 días")
        assertEquals("renovar dni", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 30), result.dueDate)
    }

    @Test
    fun testParse_InWordsWeeksAndMonths() {
        assertEquals(at(2026, Calendar.NOVEMBER, 10), parse("revisión dentro de dos semanas").dueDate)
        assertEquals(at(2026, Calendar.NOVEMBER, 27), parse("pagar seguro en un mes").dueDate)
    }

    @Test
    fun testParse_DayAfterTomorrow() {
        val result = parse("llamar pasado mañana")
        assertEquals("llamar", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 29), result.dueDate)
    }

    @Test
    fun testParse_NextWeekIsNextMonday() {
        val result = parse("planificar la próxima semana")
        assertEquals("planificar", result.title)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), result.dueDate)
    }

    @Test
    fun testParse_NextMonthIsFirstDay() {
        assertEquals(at(2026, Calendar.NOVEMBER, 1), parse("pagar cuota el mes que viene").dueDate)
    }

    @Test
    fun testParse_Weekend() {
        val result = parse("limpiar garaje este fin de semana")
        assertEquals("limpiar garaje", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 31), result.dueDate)
    }

    @Test
    fun testParse_MorningIsNotTomorrow() {
        val result = parse("correr por la mañana")
        assertEquals("correr por la mañana", result.title)
        assertNull(result.dueDate)
    }

    @Test
    fun testParse_WrittenDateReplacesDefaultDate() {
        val result = parse("llamar mañana", defaultDate = at(2026, Calendar.DECEMBER, 1, 18, 0))
        assertEquals(at(2026, Calendar.OCTOBER, 28), result.dueDate)
    }

    @Test
    fun testParse_TimeOnlyKeepsDefaultDate() {
        val result = parse("llamar a las 18", defaultDate = at(2026, Calendar.DECEMBER, 1))
        assertEquals(at(2026, Calendar.DECEMBER, 1, 18, 0), result.dueDate)
    }

    @Test
    fun testParse_TimeOnlyAlreadyPassedMovesToTomorrow() {
        // son las 10:00: "a las 8" es mañana a las 8
        assertEquals(at(2026, Calendar.OCTOBER, 28, 8, 0), parse("regar a las 8").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 27, 12, 0), parse("comer a las 12").dueDate)
    }

    @Test
    fun testParse_NumberIsNotTime() {
        val result = parse("comprar 2 panes a las 17")
        assertEquals("comprar 2 panes", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 27, 17, 0), result.dueDate)
    }

    @Test
    fun testParse_InvalidTimeIsIgnored() {
        val result = parse("leer 25:99")
        assertEquals("leer 25:99", result.title)
        assertNull(result.dueDate)
    }

    // ---------------------------------------------------------------------------------------------
    // Repetición
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParse_EveryWeekdaySetsWeeklyAndFirstDate() {
        val result = parse("sacar la basura cada lunes")
        assertEquals("sacar la basura", result.title)
        assertEquals("WEEKLY", result.recurrence)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), result.dueDate)
    }

    @Test
    fun testParse_EveryWeekdayIncludesToday() {
        assertEquals(at(2026, Calendar.OCTOBER, 27), parse("yoga todos los martes").dueDate)
    }

    @Test
    fun testParse_MondayAndWednesday() {
        val result = parse("natación los lunes y miércoles")
        assertEquals("natación", result.title)
        assertEquals("MON_WED", result.recurrence)
        assertEquals(at(2026, Calendar.OCTOBER, 28), result.dueDate)
    }

    @Test
    fun testParse_FirstDayOfMonth() {
        val result = parse("pagar alquiler el primer día de cada mes")
        assertEquals("pagar alquiler", result.title)
        assertEquals("FIRST_DAY_MONTH", result.recurrence)
        assertEquals(at(2026, Calendar.NOVEMBER, 1), result.dueDate)
    }

    @Test
    fun testParse_DailyAndMonthly() {
        assertEquals("DAILY", parse("meditar todos los días").recurrence)
        assertEquals("MONTHLY", parse("revisar facturas cada mes").recurrence)
    }

    @Test
    fun testParse_EveryAmount() {
        val result = parse("regar plantas cada 2 semanas")
        assertEquals("regar plantas", result.title)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2", result.recurrence)
        assertNull(result.dueDate)

        assertEquals("RRULE:FREQ=DAILY;INTERVAL=3", parse("cambiar agua cada tres días").recurrence)
        assertEquals("RRULE:FREQ=MONTHLY;INTERVAL=6", parse("revisión cada 6 meses").recurrence)
        assertEquals("RRULE:FREQ=YEARLY;INTERVAL=2", parse("pasaporte cada 2 años").recurrence)
        // una sola unidad es la regla de siempre
        assertEquals("WEEKLY", parse("limpiar cada 1 semana").recurrence)
        // la cantidad no se toma como hora
        assertNull(parse("pastilla cada 8 días").dueDate)
    }

    @Test
    fun testParse_EveryAmountWithWeekday() {
        val result = parse("reunión cada 2 semanas los lunes")
        assertEquals("reunión", result.title)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO", result.recurrence)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), result.dueDate)
    }

    @Test
    fun testParse_EveryAmountOtherLanguages() {
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2", parse("water plants every 2 weeks", language = "en").recurrence)
        assertEquals("RRULE:FREQ=DAILY;INTERVAL=3", parse("feed fish every three days", language = "en").recurrence)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2", parse("arroser toutes les 2 semaines", language = "fr").recurrence)
        assertEquals("RRULE:FREQ=MONTHLY;INTERVAL=3", parse("payer tous les 3 mois", language = "fr").recurrence)
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=2", parse("gießen alle 2 Wochen", language = "de").recurrence)
        assertEquals("RRULE:FREQ=DAILY;INTERVAL=3", parse("lüften alle drei Tage", language = "de").recurrence)
    }

    // ---------------------------------------------------------------------------------------------
    // Prioridad y lista
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParse_PrioritySymbols() {
        assertEquals(1, parse("leer !").priority)
        assertEquals(2, parse("leer !!").priority)
        val high = parse("entregar !!! proyecto")
        assertEquals(3, high.priority)
        assertEquals("entregar proyecto", high.title)
    }

    @Test
    fun testParse_PriorityWordsAndPhrases() {
        val word = parse("pagar multa !alta")
        assertEquals(3, word.priority)
        assertEquals("pagar multa", word.title)
        assertEquals(1, parse("ordenar fotos prioridad baja").priority)
        assertEquals(2, parse("llamar !Media").priority)
    }

    @Test
    fun testParse_ExclamationInsideTextIsNotPriority() {
        val result = parse("¡felicitar a marta!")
        assertNull(result.priority)
        assertEquals("¡felicitar a marta!", result.title)
    }

    @Test
    fun testParse_ListByName() {
        val lists = mapOf(1L to "Trabajo", 2L to "Lista de la compra")
        val work = parse("enviar informe @trabajo", lists = lists)
        assertEquals(1L, work.listId)
        assertEquals("enviar informe", work.title)
        assertNull(work.unknownList)

        assertEquals(2L, parse("leche @listadelacompra", lists = lists).listId)
        assertEquals(2L, parse("leche @lista-de-la-compra", lists = lists).listId)
    }

    @Test
    fun testParse_ListByUniquePrefix() {
        val lists = mapOf(1L to "Compras", 2L to "Casa", 3L to "Cámara")
        assertEquals(1L, parse("pan @comp", lists = lists).listId)
        // "@ca" es demasiado corto y "@cas" sigue siendo único: "casa"
        val short = parse("pan @ca", lists = lists)
        assertNull(short.listId)
        // todavía puede estar escribiendo "@casa": no se avisa de lista inexistente
        assertNull(short.unknownList)
        assertEquals(2L, parse("pan @cas", lists = lists).listId)
    }

    @Test
    fun testParse_UnknownListStaysInTitle() {
        val result = parse("enviar informe @oficina", lists = mapOf(1L to "Trabajo"))
        assertNull(result.listId)
        assertEquals("oficina", result.unknownList)
        assertEquals("enviar informe @oficina", result.title)
    }

    @Test
    fun testParse_EmailIsNotList() {
        val result = parse("escribir a ana@correo.es", lists = mapOf(1L to "correo"))
        assertNull(result.listId)
        assertNull(result.unknownList)
        assertEquals("escribir a ana@correo.es", result.title)
    }

    @Test
    fun testParse_TagsWithAccents() {
        assertEquals(listOf("café", "año-nuevo"), parse("comprar #café #año-nuevo").tags)
    }

    @Test
    fun testParse_FullSentence() {
        val lists = mapOf(7L to "Casa")
        val result = parse("Pagar la luz el viernes a las 10, !alta #facturas @casa", lists = lists)
        assertEquals("Pagar la luz", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 30, 10, 0), result.dueDate)
        assertEquals(3, result.priority)
        assertEquals(7L, result.listId)
        assertEquals(listOf("facturas"), result.tags)
    }

    // ---------------------------------------------------------------------------------------------
    // Otros idiomas
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParse_English() {
        val friday = parse("call mom on friday at 5pm !high", language = "en")
        assertEquals("call mom", friday.title)
        assertEquals(at(2026, Calendar.OCTOBER, 30, 17, 0), friday.dueDate)
        assertEquals(3, friday.priority)

        assertEquals(at(2026, Calendar.OCTOBER, 30), parse("renew passport in 3 days", language = "en").dueDate)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), parse("plan trip next week", language = "en").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 29), parse("pay day after tomorrow", language = "en").dueDate)
        assertEquals(at(2026, Calendar.NOVEMBER, 3), parse("water plants in a week", language = "en").dueDate)

        val every = parse("team sync every monday", language = "en")
        assertEquals("WEEKLY", every.recurrence)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), every.dueDate)
        assertEquals("MON_WED", parse("gym every monday and wednesday", language = "en").recurrence)
        assertEquals("DAILY", parse("stretch every day", language = "en").recurrence)
    }

    @Test
    fun testParse_English_AdjectiveInsideTitleIsKept() {
        val inside = parse("write weekly report", language = "en")
        assertEquals("write weekly report", inside.title)
        assertEquals("NONE", inside.recurrence)
        assertEquals("WEEKLY", parse("water plants weekly", language = "en").recurrence)
    }

    @Test
    fun testParse_French() {
        val result = parse("appeler le médecin vendredi à 17h30 !haute", language = "fr")
        assertEquals("appeler le médecin", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 30, 17, 30), result.dueDate)
        assertEquals(3, result.priority)

        assertEquals(at(2026, Calendar.OCTOBER, 28), parse("payer demain", language = "fr").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 29), parse("payer après-demain", language = "fr").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 30), parse("réviser dans 3 jours", language = "fr").dueDate)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), parse("ranger la semaine prochaine", language = "fr").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 27), parse("courir aujourd'hui", language = "fr").dueDate)
        assertEquals("WEEKLY", parse("yoga tous les lundis", language = "fr").recurrence)
        assertEquals("DAILY", parse("lire tous les jours", language = "fr").recurrence)
    }

    @Test
    fun testParse_German() {
        val result = parse("Arzt anrufen am Freitag um 17 Uhr !hoch", language = "de")
        assertEquals("Arzt anrufen", result.title)
        assertEquals(at(2026, Calendar.OCTOBER, 30, 17, 0), result.dueDate)
        assertEquals(3, result.priority)

        assertEquals(at(2026, Calendar.OCTOBER, 28), parse("zahlen morgen", language = "de").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 29), parse("zahlen übermorgen", language = "de").dueDate)
        assertEquals(at(2026, Calendar.OCTOBER, 30), parse("lernen in 3 Tagen", language = "de").dueDate)
        assertEquals(at(2026, Calendar.NOVEMBER, 2), parse("putzen nächste Woche", language = "de").dueDate)
        assertEquals("WEEKLY", parse("Yoga jeden Montag", language = "de").recurrence)
        assertEquals("DAILY", parse("lesen täglich", language = "de").recurrence)
        assertEquals("joggen am Morgen", parse("joggen am Morgen", language = "de").title)
    }

    @Test
    fun testParse_LanguageOnlyUsesItsOwnWords() {
        // en castellano "friday" no es una fecha
        val result = parse("call mom friday")
        assertEquals("call mom friday", result.title)
        assertNull(result.dueDate)
    }

    @Test
    fun testParse_UnknownLanguageFallsBackToSpanish() {
        assertEquals(at(2026, Calendar.OCTOBER, 28), parse("llamar mañana", language = "it").dueDate)
    }
}
