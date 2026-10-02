package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.FINDING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKING
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The park path of the lifecycle: FINDING → DRIVING → PARKING → PARKED, plus false stops.
 * @requirement QR-001
 */
class ParkingStateMachineParkTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
    private val convergeCount = CarFinderConstants.CONVERGENCE_SAMPLE_COUNT

    private val transitions = mutableListOf<Transition>()

    private fun step(snapshot: MachineSnapshot, reading: LocationReading): MachineSnapshot {
        val t = ParkingStateMachine.reduce(snapshot, MachineEvent.Reading(reading, nowEpochMillis = 0L))
        assertEquals(t.to == PARKED, t.parkedLocation != null, "invariant broken: $t")
        transitions += t
        return t.snapshot
    }

    private fun feed(snapshot: MachineSnapshot, readings: List<LocationReading>) = readings.fold(snapshot, ::step)

    // Speed readings are spread out along a road, several radii apart, so they never converge by accident.
    private var roadPosition = 0
    private fun speeds(mph: Double, n: Int = window) = List(n) {
        roadPosition++
        Readings.readingOffset(northMeters = radius * 3 * roadPosition, eastMeters = 0.0, speedMph = mph)
    }

    private fun cluster() = List(convergeCount) { i -> Readings.readingOffset(radius * 0.1 * i, 0.0) }

    private val initial = MachineSnapshot.initial()
    private fun driving() = feed(initial, speeds(Readings.DRIVING_MPH)).also { assertEquals(DRIVING, it.lifecycle) }
    private fun parking() = feed(driving(), speeds(Readings.PARKED_MPH)).also { assertEquals(PARKING, it.lifecycle) }

    /** @requirement FR-001, FR-011 */
    @Test
    fun initialState_isFindingWithNoLocation() {
        assertEquals(FINDING, initial.lifecycle)
        assertNull(initial.parkedLocation)
    }

    /** @requirement FR-003 */
    @Test
    fun finding_filteredSpeedAboveDrivingThreshold_goesToDriving() {
        assertEquals(DRIVING, driving().lifecycle)
    }

    /** @requirement FR-004 */
    @Test
    fun driving_filteredSpeedAtOrBelowParkingThreshold_goesToParking() {
        val atThreshold = feed(driving(), speeds(CarFinderConstants.PARKING_SPEED_THRESHOLD_MPH))
        assertEquals(PARKING, atThreshold.lifecycle)
    }

    /** @requirement FR-005 */
    @Test
    fun deadZoneSpeeds_causeNoChange_inDrivingOrParking() {
        assertEquals(DRIVING, feed(driving(), speeds(Readings.DEAD_ZONE_MPH)).lifecycle)
        assertEquals(PARKING, feed(parking(), speeds(Readings.DEAD_ZONE_MPH)).lifecycle)
    }

    /** @requirement FR-004, FR-011 */
    @Test
    fun finding_slowOrStationary_staysFinding_andNeverEntersParking() {
        val after = feed(initial, speeds(Readings.PARKED_MPH, n = window * 3) + speeds(0.0) + cluster())
        assertEquals(FINDING, after.lifecycle)
        assertTrue(transitions.none { it.to == PARKING })
        assertNull(after.parkedLocation)
    }

    /** @requirement FR-002, FR-006, FR-007, FR-012 */
    @Test
    fun parking_convergedReadings_goToParked_withLocationSet() {
        val parked = feed(parking(), cluster())
        assertEquals(PARKED, parked.lifecycle)
        assertNotNull(parked.parkedLocation)
        assertTrue(transitions.last().persist)
    }

    /** @requirement FR-010 */
    @Test
    fun parking_fastReadings_goToDriving_withWindowResetAndNothingStored() {
        val almostConverged = feed(parking(), cluster().dropLast(1))
        val back = feed(almostConverged, speeds(Readings.DRIVING_MPH))
        assertEquals(DRIVING, back.lifecycle)
        assertNull(back.parkedLocation)
        assertFalse(back.window.isConverged)
        assertEquals(ConvergenceWindow.empty(), back.window)
    }

    /** @requirement FR-032 */
    @Test
    fun singleRawSpike_doesNotEnterDriving() {
        val spiky = List(window) { i -> if (i == window / 2) Readings.DRIVING_MPH else Readings.PARKED_MPH }
        assertEquals(FINDING, feed(initial, spiky.map { Readings.speed(it) }).lifecycle)
    }

    /** @requirement FR-032 */
    @Test
    fun singleSlowRawReadingAtSpeed_doesNotEnterParking() {
        val after = step(driving(), Readings.speed(Readings.PARKED_MPH))
        assertEquals(DRIVING, after.lifecycle)
    }

    /** @requirement FR-032 */
    @Test
    fun nullSpeed_causesNoSpeedTransition() {
        val noSpeed = List(window * 2) { Readings.readingAt(speedMph = null) }
        assertEquals(FINDING, feed(initial, noSpeed).lifecycle)
        assertEquals(DRIVING, feed(driving(), noSpeed).lifecycle)
    }

    /** @requirement FR-018 */
    @Test
    fun restoredParkedRecordWithoutLocation_fallsBackToFinding_andAsksToPersist() {
        val record = com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord(state = PARKED)
        val t = ParkingStateMachine.reduce(initial, MachineEvent.Restored(record))
        assertEquals(FINDING, t.to)
        assertTrue(t.persist)
    }

    /** @requirement FR-001 */
    @Test
    fun reduce_isPure_sameInputGivesEqualResult_andInputIsUnchanged() {
        val snapshot = feed(parking(), cluster().dropLast(1))
        val copy = snapshot.copy()
        val event = MachineEvent.Reading(cluster().last(), nowEpochMillis = 0L)
        val first = ParkingStateMachine.reduce(snapshot, event)
        val second = ParkingStateMachine.reduce(snapshot, event)
        assertEquals(first, second)
        assertEquals(copy, snapshot)
        assertEquals(PARKING, snapshot.lifecycle)
    }
}
