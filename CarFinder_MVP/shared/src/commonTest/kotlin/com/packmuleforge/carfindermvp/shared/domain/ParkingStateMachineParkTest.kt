package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_SAMPLE_COUNT
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.FINDING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKING
import com.packmuleforge.carfindermvp.shared.testing.Readings.DEAD_ZONE_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.GOOD_ACCURACY_METERS
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingAt
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkingStateMachineParkTest {

    private val now = 1_790_000_000_000L
    private val r = CONVERGENCE_RADIUS_METERS

    /** A reading that carries only a speed, so it never enters a convergence window. */
    private fun speedOnly(mph: Double?) = readingAt(accuracyMeters = null, speedMph = mph)

    private fun speeds(mph: Double, count: Int = SPEED_FILTER_WINDOW_SIZE) = List(count) { speedOnly(mph) }

    private fun assertInvariant(snapshot: MachineSnapshot) {
        assertEquals(snapshot.lifecycle == PARKED, snapshot.parkedLocation != null, "PARKED iff a location is held")
    }

    private fun MachineSnapshot.feed(readings: List<LocationReading>): Transition {
        var snapshot = this
        var last: Transition? = null
        for (reading in readings) {
            last = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(reading, now))
            assertInvariant(last.snapshot)
            snapshot = last.snapshot
        }
        return checkNotNull(last)
    }

    private fun driving() = MachineSnapshot.INITIAL.feed(speeds(DRIVING_MPH)).snapshot.also {
        assertEquals(DRIVING, it.lifecycle)
    }

    private fun parking() = driving().feed(speeds(PARKED_MPH)).snapshot.also { assertEquals(PARKING, it.lifecycle) }

    private fun parked() = MachineSnapshot(
        lifecycle = PARKED,
        parkedLocation = ParkedLocation(readingAt().latitude, readingAt().longitude, GOOD_ACCURACY_METERS, now),
    )

    /** @requirement FR-001, FR-002 */
    @Test
    fun initialSnapshotIsFindingWithNoLocation() {
        assertEquals(FINDING, MachineSnapshot.INITIAL.lifecycle)
        assertNull(MachineSnapshot.INITIAL.parkedLocation)
    }

    /** @requirement FR-004 */
    @Test
    fun givenFinding_whenSmoothedSpeedExceedsTheDrivingThreshold_thenDriving() {
        val transition = MachineSnapshot.INITIAL.feed(speeds(DRIVING_MPH))
        assertEquals(DRIVING, transition.snapshot.lifecycle)
        assertEquals(FINDING, transition.from)
        assertTrue(transition.persist)
    }

    /** @requirement FR-005 */
    @Test
    fun givenDriving_whenSmoothedSpeedIsAtOrBelowTheParkingThreshold_thenParking() {
        assertEquals(PARKING, driving().feed(speeds(PARKED_MPH)).snapshot.lifecycle)
        assertEquals(PARKING, driving().feed(speeds(PARKING_SPEED_THRESHOLD_MPH)).snapshot.lifecycle)
    }

    /** @requirement FR-005 */
    @Test
    fun givenFinding_whenSlowOrStationary_thenNeverParking() {
        for (mph in listOf(0.0, PARKED_MPH, PARKING_SPEED_THRESHOLD_MPH)) {
            val snapshot = MachineSnapshot.INITIAL.feed(speeds(mph, SPEED_FILTER_WINDOW_SIZE * 3)).snapshot
            assertEquals(FINDING, snapshot.lifecycle)
            assertNull(snapshot.parkedLocation)
        }
    }

    /** @requirement FR-006 */
    @Test
    fun givenAnyState_whenSmoothedSpeedIsInTheDeadZone_thenNoTransition() {
        for (start in listOf(MachineSnapshot.INITIAL, driving(), parking(), parked())) {
            val transition = start.feed(speeds(DEAD_ZONE_MPH, SPEED_FILTER_WINDOW_SIZE * 2))
            assertEquals(start.lifecycle, transition.snapshot.lifecycle)
            assertEquals(start.parkedLocation, transition.snapshot.parkedLocation)
            assertFalse(transition.persist)
        }
    }

    /** @requirement FR-008 */
    @Test
    fun givenTheFilterIsNotFull_whenAVeryFastReadingArrives_thenNoTransition() {
        val transition = MachineSnapshot.INITIAL.feed(speeds(DRIVING_MPH * 4, SPEED_FILTER_WINDOW_SIZE - 1))
        assertEquals(FINDING, transition.snapshot.lifecycle)
        assertNull(transition.snapshot.speedFilter.smoothed)
    }

    /** @requirement FR-009 */
    @Test
    fun givenAReadingWithoutSpeed_thenNoSpeedTransitionEvenIfTheFilterIsPastAThreshold() {
        val fastFilter = speeds(DRIVING_MPH).fold(SpeedFilter()) { f, x -> f.add(x.speedMph!!) }
        val slowFilter = speeds(PARKED_MPH).fold(SpeedFilter()) { f, x -> f.add(x.speedMph!!) }
        val finding = MachineSnapshot(lifecycle = FINDING, speedFilter = fastFilter)
        val drivingWithSlowFilter = MachineSnapshot(lifecycle = DRIVING, speedFilter = slowFilter)
        assertEquals(FINDING, finding.feed(listOf(speedOnly(null))).snapshot.lifecycle)
        assertEquals(DRIVING, drivingWithSlowFilter.feed(listOf(speedOnly(null))).snapshot.lifecycle)
        assertEquals(fastFilter, finding.feed(listOf(speedOnly(null))).snapshot.speedFilter)
    }

    /** @requirement FR-003, FR-012, FR-015 */
    @Test
    fun givenParking_whenThreeUsableReadingsConverge_thenParkedWithTheCentroidAndDeclarationTime() {
        val readings = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, speedMph = PARKED_MPH)
        val expectedWindow = readings.fold(ConvergenceWindow()) { w, x -> w.add(x) }
        val transition = parking().feed(readings)
        val parked = assertNotNull(transition.snapshot.parkedLocation)
        assertEquals(PARKED, transition.snapshot.lifecycle)
        assertEquals(PARKING, transition.from)
        assertTrue(transition.persist)
        assertEquals(now, parked.declaredAtEpochMillis)
        assertEquals(expectedWindow.toParkedLocation(now), parked)
    }

    /** @requirement FR-012 */
    @Test
    fun theReadingThatCausesParkingEntryIsNotInTheWindow() {
        var snapshot = driving()
        var entry: Transition? = null
        while (snapshot.lifecycle == DRIVING) {
            entry = ParkingStateMachine.reduce(
                snapshot,
                MachineEvent.Reading(readingOffset(0.0, 0.0, speedMph = PARKED_MPH), now),
            )
            snapshot = entry.snapshot
        }
        assertEquals(PARKING, assertNotNull(entry).snapshot.lifecycle)
        assertTrue(entry.snapshot.window.readings.isEmpty())
    }

    /** @requirement FR-013 */
    @Test
    fun givenParking_whenReadingsNeverConverge_thenTheWindowKeepsSlidingWithNoOtherState() {
        var snapshot = parking()
        repeat(CONVERGENCE_SAMPLE_COUNT * 20) { i ->
            snapshot = snapshot.feed(listOf(readingOffset(r * 2 * i, 0.0, speedMph = PARKED_MPH))).snapshot
            assertEquals(PARKING, snapshot.lifecycle)
            assertTrue(snapshot.window.readings.size <= CONVERGENCE_SAMPLE_COUNT)
        }
        assertEquals(CONVERGENCE_SAMPLE_COUNT, snapshot.window.readings.size)
    }

    /** @requirement FR-014 */
    @Test
    fun givenParking_whenSmoothedSpeedExceedsTheDrivingThreshold_thenDrivingWithTheWindowDiscarded() {
        val partial = parking().feed(listOf(readingOffset(0.0, 0.0), readingOffset(1.0, 0.0))).snapshot
        assertEquals(2, partial.window.readings.size)
        val transition = partial.feed(speeds(DRIVING_MPH))
        assertEquals(DRIVING, transition.snapshot.lifecycle)
        assertTrue(transition.snapshot.window.readings.isEmpty())
        assertNull(transition.snapshot.parkedLocation)
    }

    /** @requirement FR-017 */
    @Test
    fun afterEveryStepParkedHoldsALocationAndNothingElseDoes() {
        // feed() asserts the invariant after every reduction.
        parking().feed(pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10)).snapshot.feed(speeds(DRIVING_MPH))
    }

    /** @requirement FR-001 */
    @Test
    fun reduceLeavesItsInputUnchangedAndIsDeterministic() {
        val input = parking()
        val copy = input.copy()
        val event = MachineEvent.Reading(readingOffset(0.0, 0.0, speedMph = PARKED_MPH), now)
        val first = ParkingStateMachine.reduce(input, event)
        val second = ParkingStateMachine.reduce(input, event)
        assertEquals(copy, input)
        assertEquals(first, second)
    }
}
