package com.packmuleforge.carfindermvp.shared.platform.android

import android.content.Intent
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeActivityPort : ActivityPort {
    var onIntent: ((Intent) -> Unit)? = null
    var throwOnRequest: Exception? = null
    var removed = false

    override fun requestInVehicleTransitions(onIntent: (Intent) -> Unit) {
        throwOnRequest?.let { throw it }
        this.onIntent = onIntent
    }

    override fun removeUpdates() {
        removed = true
        onIntent = null
    }

    fun deliver(activityType: Int, transitionType: Int) {
        val result = ActivityTransitionResult(listOf(ActivityTransitionEvent(activityType, transitionType, 0L)))
        val intent = Intent()
        SafeParcelableSerializer.serializeToIntentExtra(result, intent, TRANSITION_RESULT_EXTRA)
        onIntent?.invoke(intent)
    }

    companion object {
        // The extra Play Services uses for ActivityTransitionResult.extractResult().
        const val TRANSITION_RESULT_EXTRA = "com.google.android.location.internal.EXTRA_ACTIVITY_TRANSITION_RESULT"
    }
}

/**
 * The IN_VEHICLE hint: parsing, filtering and failure handling, through a fake Activity Recognition port.
 * @requirement QR-004
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityTransitionSourceTest {

    private val port = FakeActivityPort()
    private val source = ActivityTransitionSource(port)

    /** @requirement FR-006 */
    @Test
    fun inVehicleEnter_emitsOnce() = runTest {
        var count = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            source.inVehicleEntered.collect { count++ }
        }
        source.start()
        port.deliver(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        assertEquals(1, count)
    }

    /** @requirement FR-006 */
    @Test
    fun exitOrOtherActivities_emitNothing() = runTest {
        var count = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            source.inVehicleEntered.collect { count++ }
        }
        source.start()
        port.deliver(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
        port.deliver(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
        port.onIntent?.invoke(Intent("unrelated"))
        assertEquals(0, count)
    }

    /** @requirement FR-006 */
    @Test
    fun deniedPermission_emitsNothing_andDoesNotCrash() {
        port.throwOnRequest = SecurityException("ACTIVITY_RECOGNITION not granted")
        source.start()
        assertTrue(port.onIntent == null)
        source.stop()
        assertTrue(port.removed)
    }
}
