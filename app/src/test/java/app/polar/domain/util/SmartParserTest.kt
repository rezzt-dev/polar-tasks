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
}
