package com.packmuleforge.carfindermvp.shared.platform.android

import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.DetectedActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-003 */
class ActivityTransitionSourceTest {

    private class FakeActivityPort(var hasPermission: Boolean = true) : ActivityPort {
        var listener: ((ActivityTransitionEvent) -> Unit)? = null
        var registerCount = 0
        var unregisterCount = 0

        override fun hasActivityRecognitionPermission() = hasPermission

        override fun register(onTransition: (ActivityTransitionEvent) -> Unit) {
            registerCount++
            listener = onTransition
        }

        override fun unregister() {
            unregisterCount++
            listener = null
        }

        fun deliver(activityType: Int, transitionType: Int) =
            checkNotNull(listener)(ActivityTransitionEvent(activityType, transitionType))
    }

    private val port = FakeActivityPort()
    private val source = ActivityTransitionSource(port)

    /** @requirement FR-028 */
    @Test
    fun inVehicleIsTrueFromTheEnterTransitionToTheExitTransition() {
        source.start()
        assertFalse(source.isInVehicle.value)
        port.deliver(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        assertTrue(source.isInVehicle.value)
        port.deliver(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
        assertFalse(source.isInVehicle.value)
    }

    /** @requirement FR-028 */
    @Test
    fun otherActivitiesChangeNothing() {
        source.start()
        port.deliver(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        port.deliver(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        port.deliver(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
        assertTrue(source.isInVehicle.value)
    }

    /** @requirement FR-049 */
    @Test
    fun withoutActivityRecognitionPermissionStartRegistersNothingAndDoesNotThrow() {
        port.hasPermission = false
        source.start()
        assertEquals(0, port.registerCount)
        assertNull(port.listener)
        assertFalse(source.isInVehicle.value)
    }

    /** @requirement FR-028 */
    @Test
    fun stopUnregistersAndClearsTheSignal() {
        source.start()
        port.deliver(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        source.stop()
        assertEquals(1, port.unregisterCount)
        assertFalse(source.isInVehicle.value)
    }
}
