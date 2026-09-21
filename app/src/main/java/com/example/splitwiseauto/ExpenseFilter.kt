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
        DateFilterType.LAST_WEEK -> "Last week"
        DateFilterType.LAST_MONTH -> "Last month"
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
        WITHOUT_ME -> "Without me"
        ONLY_ME -> "Only me"
        ANY_FRIEND -> "Any friend"
    }
}
