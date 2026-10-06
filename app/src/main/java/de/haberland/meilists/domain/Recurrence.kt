package de.haberland.meilists.domain

import java.time.Instant
import java.time.ZoneId

fun validRepeatDays(days: Int?): Int? = days?.takeIf { it in 1..3650 }

/**
 * Repeating list items become due at the start of the local calendar day,
 * not exactly N * 24 hours after they were completed.
 */
fun nextRepeatDueAt(
    days: Int?,
    completedAt: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? = validRepeatDays(days)?.let { repeatDays ->
    Instant.ofEpochMilli(completedAt)
        .atZone(zone)
        .toLocalDate()
        .plusDays(repeatDays.toLong())
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}

/** Due items are open on every client without a competing cloud reset write. */
fun isEffectivelyChecked(checked: Boolean, days: Int?, nextDueAt: Long?, now: Long): Boolean =
    checked && !(validRepeatDays(days) != null && nextDueAt != null && nextDueAt <= now)
