package com.packmuleforge.carfindermvp.shared.domain

/**
 * The last [CarFinderConstants.SPEED_FILTER_WINDOW_SIZE] speeds, in mph, from readings that carry one.
 * [smoothed] is their median once the window is full, and `null` before that. Immutable.
 *
 * @requirement FR-007, FR-008
 */
data class SpeedFilter(val speedsMph: List<Double> = emptyList()) {

    val smoothed: Double?
        get() = if (speedsMph.size < CarFinderConstants.SPEED_FILTER_WINDOW_SIZE) null else median(speedsMph)

    fun add(speedMph: Double): SpeedFilter =
        SpeedFilter((speedsMph + speedMph).takeLast(CarFinderConstants.SPEED_FILTER_WINDOW_SIZE))

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
