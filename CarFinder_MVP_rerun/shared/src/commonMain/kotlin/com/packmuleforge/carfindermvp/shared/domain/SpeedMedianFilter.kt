package com.packmuleforge.carfindermvp.shared.domain

/**
 * Rolling median over the most recent speed samples, so a single anomalous reading cannot trigger a speed-based
 * transition. It is a median, not a count of consecutive readings. Immutable: [add] returns a new filter.
 *
 * @requirement FR-032
 */
class SpeedMedianFilter private constructor(
    private val speeds: List<Double>,
    val windowSize: Int,
) {
    init {
        require(windowSize > 0) { "windowSize must be positive" }
    }

    /** The median once the window is full, otherwise null (no speed transition may fire). */
    val filtered: Double? = if (speeds.size < windowSize) null else median(speeds)

    fun add(speedMph: Double): SpeedMedianFilter = SpeedMedianFilter((speeds + speedMph).takeLast(windowSize), windowSize)

    override fun equals(other: Any?) =
        other is SpeedMedianFilter && other.windowSize == windowSize && other.speeds == speeds

    override fun hashCode() = 31 * speeds.hashCode() + windowSize

    override fun toString() = "SpeedMedianFilter(speeds=$speeds, windowSize=$windowSize)"

    companion object {
        fun empty(windowSize: Int = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE) =
            SpeedMedianFilter(emptyList(), windowSize)

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}
