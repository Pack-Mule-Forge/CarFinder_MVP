package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.DRIVING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.FINDING
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKED
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState.PARKING
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drive-away: PARKED exits only to DRIVING, on filtered speed, and deletes the Parked Location.
 * @requirement QR-001
 */
class ParkingStateMachineDriveAwayTest {

    private val window = CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
    private val location = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L)
    private val parked = MachineSnapshot(PARKED, location, SpeedMedianFilter.empty(), ConvergenceWindow.empty())

    private val transitions = mutableListOf<Transition>()

    private fun feed(mphs: List<Double>): MachineSnapshot = mphs.fold(parked) { s, mph ->
        ParkingStateMachine.reduce(s, MachineEvent.Reading(Readings.speed(mph), nowEpochMillis = 0L))
            .also { transitions += it }.snapshot
    }

    /** @requirement FR-003, FR-015 */
    @Test
    fun parked_filteredSpeedAboveDrivingThreshold_goesToDriving_andDeletesLocation() {
        val after = feed(List(window) { Readings.DRIVING_MPH })
        assertEquals(DRIVING, after.lifecycle)
        assertNull(after.parkedLocation)
        val exit = transitions.single { it.from == PARKED && it.to == DRIVING }
        assertTrue(exit.persist)
    }

    /** @requirement FR-032 */
    @Test
    fun parked_singleRawSpike_staysParked_withLocationKept() {
        val spiky = List(window) { if (it == window / 2) Readings.DRIVING_MPH else Readings.PARKED_MPH }
        val after = feed(spiky)
        assertEquals(PARKED, after.lifecycle)
        assertEquals(location, after.parkedLocation)
    }

    /** @requirement FR-005, FR-009 */
    @Test
    fun parked_deadZoneOrSlowSpeeds_stayParked() {
        assertEquals(PARKED, feed(List(window * 3) { Readings.DEAD_ZONE_MPH }).lifecycle)
        assertEquals(PARKED, feed(List(window * 3) { Readings.PARKED_MPH }).lifecycle)
    }

    /** @requirement FR-009 */
    @Test
    fun parked_neverGoesToFindingOrParking() {
        feed(List(window * 2) { Readings.PARKED_MPH } + List(window * 2) { Readings.DEAD_ZONE_MPH } + List(window) { 0.0 })
        assertTrue(transitions.none { it.from == PARKED && (it.to == FINDING || it.to == PARKING) })
    }
}
