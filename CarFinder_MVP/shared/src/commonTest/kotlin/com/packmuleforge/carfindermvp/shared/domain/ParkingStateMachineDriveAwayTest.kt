package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONVERGENCE_RADIUS_METERS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_PARKING_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.shared.testing.Readings.DEAD_ZONE_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.DRIVING_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.PARKED_MPH
import com.packmuleforge.carfindermvp.shared.testing.Readings.pairwiseTriangle
import com.packmuleforge.carfindermvp.shared.testing.Readings.readingAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkingStateMachineDriveAwayTest {

    private val declaredAt = 1_790_000_000_000L
    private val location = ParkedLocation(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS, declaredAt)

    /** PARKED, with the speed filter already full of slow speeds and the recovery window long closed. */
    private val parked = MachineSnapshot(
        lifecycle = PARKED,
        parkedLocation = location,
        speedFilter = List(SPEED_FILTER_WINDOW_SIZE) { PARKED_MPH }.fold(SpeedFilter()) { f, x -> f.add(x) },
    )
    private val afterWindow = declaredAt + CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS * 10

    private fun speedOnly(mph: Double) = readingAt(accuracyMeters = null, speedMph = mph)

    private fun MachineSnapshot.feed(speeds: List<Double>, now: Long = afterWindow): Transition {
        var snapshot = this
        var last: Transition? = null
        for (mph in speeds) {
            last = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(speedOnly(mph), now))
            assertEquals(last.snapshot.lifecycle == PARKED, last.snapshot.parkedLocation != null)
            snapshot = last.snapshot
        }
        return checkNotNull(last)
    }

    /** @requirement FR-004, FR-019 */
    @Test
    fun givenParked_whenSmoothedSpeedExceedsTheDrivingThreshold_thenDrivingWithTheLocationDeleted() {
        var snapshot = parked
        var driveAway: Transition? = null
        while (driveAway == null) {
            val transition = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(speedOnly(DRIVING_MPH), afterWindow))
            if (transition.snapshot.lifecycle != PARKED) driveAway = transition
            snapshot = transition.snapshot
        }
        assertEquals(PARKED, driveAway.from)
        assertEquals(DRIVING, driveAway.snapshot.lifecycle)
        assertNull(driveAway.snapshot.parkedLocation)
        assertTrue(driveAway.persist)
    }

    /** @requirement FR-007 */
    @Test
    fun oneFastReadingAmongSlowOnesKeepsParkedAndTheLocation() {
        val transition = parked.feed(listOf(PARKED_MPH, DRIVING_MPH * 4, PARKED_MPH, PARKED_MPH))
        assertEquals(PARKED, transition.snapshot.lifecycle)
        assertEquals(location, transition.snapshot.parkedLocation)
    }

    /** @requirement FR-006 */
    @Test
    fun deadZoneAndSlowSpeedsKeepParked() {
        for (mph in listOf(DEAD_ZONE_MPH, PARKED_MPH, 0.0)) {
            val transition = parked.feed(List(SPEED_FILTER_WINDOW_SIZE * 3) { mph })
            assertEquals(PARKED, transition.snapshot.lifecycle)
            assertEquals(location, transition.snapshot.parkedLocation)
        }
    }

    /** @requirement FR-002, FR-019 */
    @Test
    fun parkedNeverBecomesFindingOrParking() {
        val speeds = listOf(0.0, PARKED_MPH, DEAD_ZONE_MPH, DRIVING_MPH)
        for (a in speeds) for (b in speeds) for (c in speeds) {
            val state = parked.feed(listOf(a, b, c)).snapshot.lifecycle
            assertTrue(state == PARKED || state == DRIVING, "$a $b $c gave $state")
        }
    }

    /** @requirement FR-026 */
    @Test
    fun insideTheRecoveryWindowAFastSmoothedSpeedDrivesAwayAndDoesNotCorrect() {
        val r = CONVERGENCE_RADIUS_METERS
        val readings = pairwiseTriangle(r * 4 / 10, r * 6 / 10, r * 9 / 10, northOriginMeters = r * 5,
            spacingMillis = SAMPLING_INTERVAL_PARKING_MILLIS)
        var snapshot = parked
        for (reading in readings.take(2)) {
            snapshot = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(reading, declaredAt + 1)).snapshot
        }
        snapshot = snapshot.feed(listOf(DRIVING_MPH), now = declaredAt + 2).snapshot
        val fastThird = readings.last().copy(speedMetersPerSecond = Readings.mphToMetersPerSecond(DRIVING_MPH))
        val transition = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(fastThird, declaredAt + 3))
        // Drive-away wins: the location is deleted rather than corrected.
        assertEquals(DRIVING, transition.snapshot.lifecycle)
        assertNull(transition.snapshot.parkedLocation)
    }
}
