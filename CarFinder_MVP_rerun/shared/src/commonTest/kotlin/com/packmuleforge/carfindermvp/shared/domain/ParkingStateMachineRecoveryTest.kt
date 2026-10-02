package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.LatLon
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bounded re-convergence recovery: a premature PARKED is silently corrected to a newly converged location, but
 * only inside the recovery window measured from the PARKED declaration.
 * @requirement QR-001
 */
class ParkingStateMachineRecoveryTest {

    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private val sampleCount = CarFinderConstants.CONVERGENCE_SAMPLE_COUNT
    private val recoveryWindow = CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
    private val spacing = TuningConstants.RECOVERY_MIN_SAMPLE_SPACING_MILLIS

    private val declaredAt = recoveryWindow * 10
    private val farNorth = radius * 20
    private val original = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, declaredAt)
    private val parked = MachineSnapshot(PARKED, original, SpeedMedianFilter.empty(), ConvergenceWindow.empty())

    private val transitions = mutableListOf<Transition>()

    /** One timed reading: [atMillis] is the offset from the PARKED declaration on both clocks. */
    private data class Sample(val reading: LocationReading, val atMillis: Long)

    /** A cluster well inside the convergence radius, [northMeters] from the original location. */
    private fun cluster(
        northMeters: Double,
        firstAtMillis: Long,
        stepMillis: Long = spacing,
        accuracy: Double? = Readings.GOOD_ACCURACY_METERS,
        speedMph: Double? = Readings.PARKED_MPH,
    ) = List(sampleCount) { i ->
        val at = firstAtMillis + stepMillis * i
        Sample(Readings.readingOffset(northMeters + radius * 0.1 * i, 0.0, accuracy, speedMph, elapsedMillis = at), at)
    }

    private fun feed(samples: List<Sample>, from: MachineSnapshot = parked): MachineSnapshot =
        samples.fold(from) { s, sample ->
            ParkingStateMachine.reduce(s, MachineEvent.Reading(sample.reading, declaredAt + sample.atMillis))
                .also { transitions += it }.snapshot
        }

    private fun centroidOf(samples: List<Sample>) =
        GeoMath.centroid(samples.map { LatLon(it.reading.latitude, it.reading.longitude) })

    /** @requirement FR-035 */
    @Test
    fun parkedWithinRecoveryWindow_newConvergence_correctsLocation_andStaysParked() {
        val samples = cluster(farNorth, firstAtMillis = spacing)

        val after = feed(samples)

        assertEquals(PARKED, after.lifecycle)
        val corrected = after.parkedLocation!!
        val centroid = centroidOf(samples)
        assertEquals(centroid.latitude, corrected.latitude, 1e-12)
        assertEquals(centroid.longitude, corrected.longitude, 1e-12)
        assertTrue(transitions.all { it.from == PARKED && it.to == PARKED }, "a correction is not a lifecycle change")
        assertTrue(transitions.last().persist, "the corrected location must be persisted")
        assertTrue(transitions.dropLast(1).none { it.persist })
    }

    /** @requirement FR-035 */
    @Test
    fun parkedAfterRecoveryWindow_newConvergence_keepsOriginalLocation() {
        val after = feed(cluster(farNorth, firstAtMillis = recoveryWindow + 1))

        assertEquals(PARKED, after.lifecycle)
        assertEquals(original, after.parkedLocation)
        assertTrue(transitions.none { it.persist })
    }

    /** @requirement FR-035 */
    @Test
    fun convergenceCompletingExactlyAtWindowEnd_corrects_oneMillisecondLater_doesNot() {
        val span = spacing * (sampleCount - 1)

        val atEdge = feed(cluster(farNorth, firstAtMillis = recoveryWindow - span))
        val pastEdge = feed(cluster(farNorth, firstAtMillis = recoveryWindow - span + 1))

        assertNotEquals(original, atEdge.parkedLocation)
        assertEquals(original, pastEdge.parkedLocation)
    }

    /** @requirement FR-035 */
    @Test
    fun correction_keepsDeclarationTime_soTheWindowIsNeverExtended() {
        val firstStop = cluster(farNorth, firstAtMillis = recoveryWindow / 2)
        val corrected = feed(firstStop)
        val correctedLocation = corrected.parkedLocation!!
        assertEquals(declaredAt, correctedLocation.capturedAtEpochMillis)

        // Inside a window measured from the correction, but outside the one measured from the declaration.
        val after = feed(cluster(farNorth * 2, firstAtMillis = recoveryWindow + 1), from = corrected)

        assertEquals(correctedLocation, after.parkedLocation)
    }

    /** @requirement FR-035 */
    @Test
    fun partialReadingsAreDiscarded_whenTheWindowCloses() {
        val straddling = cluster(farNorth, firstAtMillis = recoveryWindow - spacing)

        val after = feed(straddling + cluster(farNorth, firstAtMillis = recoveryWindow * 2))

        assertEquals(original, after.parkedLocation)
        assertEquals(ConvergenceWindow.empty(), after.window)
    }

    /** @requirement FR-035 */
    @Test
    fun readingsFasterThanMinimumSpacing_areThinned_soAMovingPhoneDoesNotConverge() {
        val fast = cluster(farNorth, firstAtMillis = spacing, stepMillis = spacing / sampleCount)

        val after = feed(fast)

        assertEquals(original, after.parkedLocation)
        assertFalse(after.window.isConverged)
    }

    /** @requirement FR-035 */
    @Test
    fun spreadReadings_doNotCorrect() {
        val spread = List(sampleCount * 3) { i ->
            val at = spacing * (i + 1)
            Sample(Readings.readingOffset(farNorth + radius * 0.9 * i, 0.0, elapsedMillis = at), at)
        }

        assertEquals(original, feed(spread).parkedLocation)
    }

    /** @requirement FR-035 */
    @Test
    fun readingsWithoutAccuracy_areNotUsedForRecovery() {
        val after = feed(cluster(farNorth, firstAtMillis = spacing, accuracy = null))

        assertEquals(original, after.parkedLocation)
    }

    /** @requirement FR-015, FR-035 */
    @Test
    fun drivingSpeedDuringRecovery_stillDrivesAway_andDeletesLocation() {
        val fastCluster = cluster(farNorth, firstAtMillis = spacing, speedMph = Readings.DRIVING_MPH)

        val after = feed(fastCluster)

        assertEquals(DRIVING, after.lifecycle)
        assertNull(after.parkedLocation)
        assertEquals(ConvergenceWindow.empty(), after.window)
    }

    /** @requirement FR-035 */
    @Test
    fun wallClockEarlierThanDeclaration_doesNotCorrect() {
        val after = feed(cluster(farNorth, firstAtMillis = spacing).map { it.copy(atMillis = -it.atMillis) })

        assertEquals(original, after.parkedLocation)
    }

    /** @requirement FR-035 */
    @Test
    fun isWithinRecoveryWindow_isBoundedOnBothSides() {
        assertTrue(original.isWithinRecoveryWindow(declaredAt))
        assertTrue(original.isWithinRecoveryWindow(declaredAt + recoveryWindow))
        assertFalse(original.isWithinRecoveryWindow(declaredAt + recoveryWindow + 1))
        assertFalse(original.isWithinRecoveryWindow(declaredAt - 1))
    }
}
