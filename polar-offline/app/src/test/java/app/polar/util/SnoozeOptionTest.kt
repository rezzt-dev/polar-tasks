package app.polar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SnoozeOptionTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun SnoozeOption.from(now: Long) = at(now, defaultHour = 7, defaultMinute = 30)

    @Test
    fun relativeOptions_roundToTheMinute() {
        val now = at(2026, 1, 14, 10, 12, 41)
        assertEquals(at(2026, 1, 14, 10, 27), SnoozeOption.MINUTES_15.from(now))
        assertEquals(at(2026, 1, 14, 11, 12), SnoozeOption.HOUR_1.from(now))
        assertEquals(at(2026, 1, 14, 13, 12), SnoozeOption.HOURS_3.from(now))
    }

    @Test
    fun relativeOptions_crossMidnight() {
        assertEquals(at(2026, 1, 15, 1, 30), SnoozeOption.HOURS_3.from(at(2026, 1, 14, 22, 30)))
    }

    @Test
    fun thisEvening_onlyWhileThereIsTimeLeft() {
        assertEquals(at(2026, 1, 14, 18, 0), SnoozeOption.THIS_EVENING.from(at(2026, 1, 14, 9, 0)))
        assertEquals(at(2026, 1, 14, 18, 0), SnoozeOption.THIS_EVENING.from(at(2026, 1, 14, 16, 59)))
        assertNull(SnoozeOption.THIS_EVENING.from(at(2026, 1, 14, 17, 0)))
        assertNull(SnoozeOption.THIS_EVENING.from(at(2026, 1, 14, 21, 0)))
    }

    @Test
    fun tomorrow_usesTheDefaultNotificationTime() {
        assertEquals(at(2026, 1, 15, 7, 30), SnoozeOption.TOMORROW.from(at(2026, 1, 14, 10, 0)))
        // Fin de mes y de año
        assertEquals(at(2027, 1, 1, 7, 30), SnoozeOption.TOMORROW.from(at(2026, 12, 31, 23, 59)))
        assertEquals(at(2026, 1, 15, 9, 0), SnoozeOption.TOMORROW.at(at(2026, 1, 14, 10, 0), 9, 0))
    }
}
