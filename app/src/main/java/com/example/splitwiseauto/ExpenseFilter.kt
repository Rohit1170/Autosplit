package com.example.splitwiseauto

/**
 * Date-range side of the Home tab's expense filter. ALL keeps the exact
 * unfiltered behavior from before this feature existed. CUSTOM is a
 * user-picked [startMillis, endMillis] range, optionally named — naming it
 * is what makes it eligible to be saved as a [SavedFilter] for later reuse.
 */
enum class DateFilterType { ALL, LAST_WEEK, LAST_MONTH, CUSTOM }

data class DateFilter(
    val type: DateFilterType = DateFilterType.ALL,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val label: String? = null
) {
    val isActive: Boolean get() = type != DateFilterType.ALL

    fun summary(): String = when (type) {
        DateFilterType.ALL -> "All time"
        DateFilterType.LAST_WEEK -> "Last 7 days"
        DateFilterType.LAST_MONTH -> "Last 30 days"
        DateFilterType.CUSTOM -> label?.takeIf { it.isNotBlank() } ?: "Custom range"
    }
}

/** Who an expense was split with. ALL keeps the exact unfiltered behavior. */
enum class ParticipantFilter {
    ALL, WITH_ME, WITHOUT_ME, ONLY_ME, ANY_FRIEND;

    val isActive: Boolean get() = this != ALL

    fun summary(): String = when (this) {
        ALL -> "Everyone"
        WITH_ME -> "With me"
        WITHOUT_ME -> "For others"
        ONLY_ME -> "Just me"
        ANY_FRIEND -> "Shared"
    }
}

/**
 * Order of the Home "Recent Expenses" list. NEWEST is the default (grouped
 * under month bars); the amount sorts show a flat list, since month bars
 * make no sense once rows are no longer in date order.
 */
enum class AmountSort {
    NEWEST, HIGH_TO_LOW, LOW_TO_HIGH;

    /** Tap order of the single sort icon: newest -> high->low -> low->high -> newest. */
    fun next(): AmountSort = when (this) {
        NEWEST -> HIGH_TO_LOW
        HIGH_TO_LOW -> LOW_TO_HIGH
        LOW_TO_HIGH -> NEWEST
    }
}
