package de.haberland.meilists

import de.haberland.meilists.domain.isEffectivelyChecked
import de.haberland.meilists.domain.nextRepeatDueAt
import de.haberland.meilists.domain.validRepeatDays
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class RecurrenceTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test fun repeatIntervalBecomesDueAtStartOfLocalCalendarDay() {
        val completed = LocalDate.of(2026, 10, 4)
            .atTime(18, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val expectedDue = LocalDate.of(2026, 10, 6)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val due = nextRepeatDueAt(2, completed, zone)!!
        assertEquals(expectedDue, due)
        assertTrue(isEffectivelyChecked(true, 2, due, due - 1))
        assertFalse(isEffectivelyChecked(true, 2, due, due))
        assertFalse(isEffectivelyChecked(true, 2, due, due + 7 * 86_400_000L))
    }

    @Test fun recurrenceHandlesDaylightSavingCalendarDays() {
        val completed = LocalDate.of(2026, 10, 24)
            .atTime(20, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val expectedDue = LocalDate.of(2026, 10, 26)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedDue, nextRepeatDueAt(2, completed, zone))
    }

    @Test fun openItemsAndDisabledRecurrenceKeepTheirState() {
        assertFalse(isEffectivelyChecked(false, 2, null, Long.MAX_VALUE))
        assertTrue(isEffectivelyChecked(true, null, 1, Long.MAX_VALUE))
        assertTrue(isEffectivelyChecked(true, 2, null, Long.MAX_VALUE))
        assertTrue(isEffectivelyChecked(true, 0, 1, Long.MAX_VALUE))
        assertNull(nextRepeatDueAt(null, 100, zone))
    }

    @Test fun invalidIntervalsAreRejected() {
        for (value in listOf(null, -1, 0, 3651, Int.MAX_VALUE)) assertNull(validRepeatDays(value))
        assertEquals(1, validRepeatDays(1)!!)
        assertEquals(3650, validRepeatDays(3650)!!)
    }
}
