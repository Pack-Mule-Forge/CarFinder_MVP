package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkingStateMachineRecoveryTest {

    private val r = CONVERGENCE_RADIUS_METERS
    private val interval = SAMPLING_INTERVAL_PARKING_MILLIS
    private val declaredAt = 1_790_000_000_000L
    private val original = ParkedLocation(
        Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS, declaredAt,
    )

    /** A PARKED snapshot built directly: no DRIVING state precedes it. */
    private val parked = MachineSnapshot(lifecycle = PARKED, parkedLocation = original)

    /** Three converging readings [northMeters] north of the stored location, [interval] apart from [startElapsed]. */
    private fun convergingAt(northMeters: Double, startElapsed: Long = 0, spacing: Long = interval) =
        pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, startElapsedMillis = startElapsed, spacingMillis = spacing,
            northOriginMeters = northMeters)

    /** Feeds [readings] with wall times starting at [startWall], advancing the wall clock with each reading's spacing. */
    private fun MachineSnapshot.feed(readings: List<LocationReading>, startWall: Long): Transition {
        var snapshot = this
        var last: Transition? = null
        val firstElapsed = readings.first().receivedElapsedMillis
        for (reading in readings) {
            val wall = startWall + (reading.receivedElapsedMillis - firstElapsed)
            last = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(reading, wall))
            assertEquals(PARKED, last.from)
            assertEquals(PARKED, last.snapshot.lifecycle)
            snapshot = last.snapshot
        }
        return checkNotNull(last)
    }

    /** @requirement FR-021, FR-022, FR-023 */
    @Test
    fun aConvergenceInsideTheWindowBeyondTheRadiusReplacesTheLocation() {
        val readings = convergingAt(r * 5)
        val transition = parked.feed(readings, startWall = declaredAt + interval)
        val expected = readings.fold(ConvergenceWindow()) { w, x -> w.add(x) }.toParkedLocation(declaredAt)
        assertEquals(expected, transition.snapshot.parkedLocation)
        assertEquals(declaredAt, transition.snapshot.parkedLocation?.declaredAtEpochMillis)
        assertTrue(transition.persist)
    }

    /** @requirement FR-022 */
    @Test
    fun aConvergenceWithinTheRadiusChangesNothing() {
        val transition = parked.feed(convergingAt(r / 4), startWall = declaredAt + interval)
        assertEquals(original, transition.snapshot.parkedLocation)
        assertFalse(transition.persist)
    }

    /** @requirement FR-024 */
    @Test
    fun aConvergenceCompletedOneMillisecondAfterTheWindowChangesNothing() {
        // The third reading lands one millisecond after the window's end.
        val start = declaredAt + PARKED_RECOVERY_WINDOW_MILLIS + 1 - 2 * interval
        val transition = parked.feed(convergingAt(r * 5), startWall = start)
        assertEquals(original, transition.snapshot.parkedLocation)
        assertFalse(transition.persist)
    }

    /** @requirement FR-021 */
    @Test
    fun aConvergenceCompletedExactlyAtTheWindowEndCounts() {
        val start = declaredAt + PARKED_RECOVERY_WINDOW_MILLIS - 2 * interval
        val transition = parked.feed(convergingAt(r * 5), startWall = start)
        assertNotEquals(original, transition.snapshot.parkedLocation)
    }

    /** @requirement FR-023 */
    @Test
    fun afterACorrectionTheWindowStillEndsAtTheOriginalTime() {
        val corrected = parked.feed(convergingAt(r * 5), startWall = declaredAt + interval).snapshot
        // Inside a window counted from the correction, but outside the original one.
        val late = corrected.feed(convergingAt(r * 12, startElapsed = interval * 10), startWall = declaredAt + PARKED_RECOVERY_WINDOW_MILLIS + interval)
        assertEquals(corrected.parkedLocation, late.snapshot.parkedLocation)
    }

    /** @requirement FR-022, FR-023 */
    @Test
    fun twoCorrectionsInsideTheOriginalWindowBothApply() {
        val first = parked.feed(convergingAt(r * 5), startWall = declaredAt + interval)
        val second = first.snapshot.feed(convergingAt(r * 12, startElapsed = interval * 10), startWall = declaredAt + interval * 10)
        assertNotEquals(first.snapshot.parkedLocation, second.snapshot.parkedLocation)
        assertTrue(second.persist)
        assertEquals(declaredAt, second.snapshot.parkedLocation?.declaredAtEpochMillis)
    }

    /** @requirement FR-025 */
    @Test
    fun readingsCloserThanTheParkingIntervalAreThinnedAndDoNotCorrect() {
        // The second reading is thinned; the third is far enough from the first to be accepted.
        val transition = parked.feed(convergingAt(r * 5, spacing = interval - 1), startWall = declaredAt + interval)
        assertEquals(original, transition.snapshot.parkedLocation)
        assertEquals(2, transition.snapshot.window.readings.size)
    }

    /** @requirement FR-025 */
    @Test
    fun readingsExactlyTheParkingIntervalApartAreUsed() {
        val transition = parked.feed(convergingAt(r * 5, spacing = interval), startWall = declaredAt + interval)
        assertNotEquals(original, transition.snapshot.parkedLocation)
    }

    /** @requirement FR-010 */
    @Test
    fun readingsWithoutAccuracyAreNotUsed() {
        val readings = convergingAt(r * 5).map { it.copy(accuracyMeters = null) }
        val transition = parked.feed(readings, startWall = declaredAt + interval)
        assertEquals(original, transition.snapshot.parkedLocation)
        assertTrue(transition.snapshot.window.readings.isEmpty())
    }

    /** @requirement FR-024 */
    @Test
    fun partlyCollectedReadingsAreDiscardedWhenTheWindowCloses() {
        val partial = parked.feed(convergingAt(r * 5).take(2), startWall = declaredAt + interval).snapshot
        assertEquals(2, partial.window.readings.size)

        val byTimer = ParkingStateMachine.reduce(
            partial,
            MachineEvent.RecoveryWindowElapsed(declaredAt + PARKED_RECOVERY_WINDOW_MILLIS + 1),
        )
        assertTrue(byTimer.snapshot.window.readings.isEmpty())
        assertFalse(byTimer.persist)

        val late = convergingAt(r * 5, startElapsed = interval * 2).last()
        val byReading = ParkingStateMachine.reduce(
            partial,
            MachineEvent.Reading(late, declaredAt + PARKED_RECOVERY_WINDOW_MILLIS + 1),
        )
        assertTrue(byReading.snapshot.window.readings.isEmpty())
        assertEquals(original, byReading.snapshot.parkedLocation)
    }

    /** @requirement FR-021 */
    @Test
    fun aWallClockEarlierThanTheDeclarationCorrectsNothing() {
        val transition = parked.feed(convergingAt(r * 5), startWall = declaredAt - interval * 10)
        assertEquals(original, transition.snapshot.parkedLocation)
    }

    /** @requirement FR-026 */
    @Test
    fun aDrivingSpeedDuringTheWindowGivesNoCorrectionOnThatReading() {
        val fastFilter = List(SPEED_FILTER_WINDOW_SIZE) { DRIVING_MPH }.fold(SpeedFilter()) { f, x -> f.add(x) }
        val readings = convergingAt(r * 5)
        var snapshot = parked.copy(speedFilter = fastFilter)
        for (reading in readings.take(2)) {
            snapshot = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(reading, declaredAt + interval)).snapshot
        }
        val fastThird = readings.last().copy(speedMetersPerSecond = Readings.mphToMetersPerSecond(DRIVING_MPH))
        val transition = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(fastThird, declaredAt + interval * 3))
        val correction = readings.fold(ConvergenceWindow()) { w, x -> w.add(x) }.toParkedLocation(declaredAt)
        assertNotEquals(correction, transition.snapshot.parkedLocation)
    }
}
