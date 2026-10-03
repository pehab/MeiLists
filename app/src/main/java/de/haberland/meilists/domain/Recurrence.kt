package de.haberland.meilists.domain

private const val DAY_MILLIS = 86_400_000L

fun validRepeatDays(days: Int?): Int? = days?.takeIf { it in 1..3650 }

fun nextRepeatDueAt(days: Int?, completedAt: Long): Long? =
    validRepeatDays(days)?.let { Math.addExact(completedAt, it.toLong() * DAY_MILLIS) }

/** Due items are open on every client without a competing cloud reset write. */
fun isEffectivelyChecked(checked: Boolean, days: Int?, nextDueAt: Long?, now: Long): Boolean =
    checked && !(validRepeatDays(days) != null && nextDueAt != null && nextDueAt <= now)
