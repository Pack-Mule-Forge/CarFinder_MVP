package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import kotlinx.coroutines.flow.Flow

/**
 * Compass heading. Implementations MUST emit a true-north heading remapped to the display rotation, and MUST emit
 * null when the sensor is absent, reports unreliable accuracy, or has sent no event for
 * `CarFinderConstants.HEADING_STALENESS_TIMEOUT_MILLIS` (FR-031).
 *
 * @requirement QR-010
 */
interface HeadingSource {
    /** Null means the heading is unavailable. */
    val headings: Flow<HeadingReading?>

    /** Started only while the guidance UI is visible. */
    fun start()

    fun stop()

    /** Supplies the latest fix for magnetic-declination (true-north) correction. */
    fun updateDeclinationFrom(fix: LocationReading)
}
