package de.haberland.meilists

import de.haberland.meilists.domain.isEffectivelyChecked
import de.haberland.meilists.domain.nextRepeatDueAt
import de.haberland.meilists.domain.validRepeatDays
import org.junit.Assert.*
import org.junit.Test

class RecurrenceTest {
    @Test fun repeatIntervalStartsAtCompletionAndExpiresAtExactDeadline() {
        val completed = 1_000L
        val due = nextRepeatDueAt(2, completed)!!
        assertEquals(completed + 2 * 86_400_000L, due)
        assertTrue(isEffectivelyChecked(true, 2, due, due - 1))
        assertFalse(isEffectivelyChecked(true, 2, due, due))
        assertFalse(isEffectivelyChecked(true, 2, due, due + 7 * 86_400_000L))
        assertEquals(due + 7 * 86_400_000L + 2 * 86_400_000L,
            nextRepeatDueAt(2, due + 7 * 86_400_000L)!!)
    }

    @Test fun openItemsAndDisabledRecurrenceKeepTheirState() {
        assertFalse(isEffectivelyChecked(false, 2, null, Long.MAX_VALUE))
        assertTrue(isEffectivelyChecked(true, null, 1, Long.MAX_VALUE))
        assertTrue(isEffectivelyChecked(true, 2, null, Long.MAX_VALUE))
        assertTrue(isEffectivelyChecked(true, 0, 1, Long.MAX_VALUE))
        assertNull(nextRepeatDueAt(null, 100))
    }

    @Test fun invalidIntervalsAreRejected() {
        for (value in listOf(null, -1, 0, 3651, Int.MAX_VALUE)) assertNull(validRepeatDays(value))
        assertEquals(1, validRepeatDays(1)!!)
        assertEquals(3650, validRepeatDays(3650)!!)
    }
}
