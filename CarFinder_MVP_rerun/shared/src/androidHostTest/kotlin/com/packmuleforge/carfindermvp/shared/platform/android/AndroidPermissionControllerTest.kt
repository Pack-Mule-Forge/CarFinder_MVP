package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
class AndroidPermissionControllerTest {

    /** A registry that records launches instead of showing a system dialog. */
    private class TestRegistry : ActivityResultRegistry() {
        val launches = mutableListOf<Pair<Int, List<String>>>()

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            @Suppress("UNCHECKED_CAST")
            launches += requestCode to (input as Array<String>).toList()
        }
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val fine = Manifest.permission.ACCESS_FINE_LOCATION
    private val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

    private fun grant(vararg permissions: String) = shadowOf(app).grantPermissions(*permissions)
    private fun revoke(vararg permissions: String) = shadowOf(app).denyPermissions(*permissions)

    /** @requirement FR-047, FR-048 */
    @Test
    fun aRequestLaunchesOnePlatformRequestAndLocationNamesFineAndCoarse() = runTest {
        val registry = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(registry) }
        val answer = async { controller.request(Capability.FINE_LOCATION) }
        runCurrent()
        assertEquals(1, registry.launches.size)
        assertEquals(setOf(fine, coarse), registry.launches.single().second.toSet())
        grant(fine, coarse)
        registry.dispatchResult(registry.launches.single().first, mapOf(fine to true, coarse to true))
        assertTrue(answer.await())
        assertEquals(PermissionStatus.GRANTED, controller.status.value[Capability.FINE_LOCATION])
    }

    /** @requirement FR-056 */
    @Test
    fun approximateLocationAloneIsADenial() = runTest {
        val registry = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(registry) }
        val answer = async { controller.request(Capability.FINE_LOCATION) }
        runCurrent()
        grant(coarse)
        registry.dispatchResult(registry.launches.single().first, mapOf(fine to false, coarse to true))
        assertFalse(answer.await())
        assertEquals(PermissionStatus.DENIED, controller.status.value[Capability.FINE_LOCATION])
    }

    /** @requirement FR-048 */
    @Test
    fun aDenialIsRememberedByANewControllerOnTheSameContext() = runTest {
        val registry = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(registry) }
        val answer = async { controller.request(Capability.FINE_LOCATION) }
        runCurrent()
        registry.dispatchResult(registry.launches.single().first, mapOf(fine to false, coarse to false))
        assertFalse(answer.await())
        assertEquals(PermissionStatus.DENIED, controller.status.value[Capability.FINE_LOCATION])
        assertEquals(PermissionStatus.DENIED, AndroidPermissionController(app).status.value[Capability.FINE_LOCATION])
        assertEquals(PermissionStatus.NOT_REQUESTED, AndroidPermissionController(app).status.value[Capability.NOTIFICATIONS])
    }

    /** @requirement FR-048 */
    @Test
    @Config(sdk = [28])
    fun belowAndroid10BackgroundLocationAndActivityRecognitionNeedNoGrant() = runTest {
        val registry = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(registry) }
        assertEquals(PermissionStatus.GRANTED, controller.status.value[Capability.BACKGROUND_LOCATION])
        assertEquals(PermissionStatus.GRANTED, controller.status.value[Capability.ACTIVITY_RECOGNITION])
        assertTrue(controller.request(Capability.BACKGROUND_LOCATION))
        assertTrue(controller.request(Capability.ACTIVITY_RECOGNITION))
        assertTrue(registry.launches.isEmpty())
    }

    /** @requirement FR-048 */
    @Test
    @Config(sdk = [32])
    fun belowAndroid13NotificationsNeedNoGrant() = runTest {
        val registry = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(registry) }
        assertEquals(PermissionStatus.GRANTED, controller.status.value[Capability.NOTIFICATIONS])
        assertTrue(controller.request(Capability.NOTIFICATIONS))
        assertTrue(registry.launches.isEmpty())
    }

    /** @requirement FR-048, FR-049 */
    @Test
    fun refreshPicksUpGrantsAndRevocationsMadeOutsideTheApp() = runTest {
        val registry = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(registry) }
        val answer = async { controller.request(Capability.NOTIFICATIONS) }
        runCurrent()
        registry.dispatchResult(registry.launches.single().first, mapOf(Manifest.permission.POST_NOTIFICATIONS to false))
        answer.await()
        assertEquals(PermissionStatus.DENIED, controller.status.value[Capability.NOTIFICATIONS])
        grant(Manifest.permission.POST_NOTIFICATIONS)
        controller.refresh()
        assertEquals(PermissionStatus.GRANTED, controller.status.value[Capability.NOTIFICATIONS])
        revoke(Manifest.permission.POST_NOTIFICATIONS)
        controller.refresh()
        assertEquals(PermissionStatus.DENIED, controller.status.value[Capability.NOTIFICATIONS])
    }

    /** @requirement FR-049 */
    @Test
    fun noCallThrowsWithNoActivityAttached() = runTest {
        val controller = AndroidPermissionController(app)
        controller.refresh()
        assertFalse(controller.request(Capability.FINE_LOCATION))
        controller.detach()
    }

    /** @requirement FR-048 */
    @Test
    fun aRequestPendingAcrossActivityRecreationResumesWithTheResultDeliveredToTheNewRegistry() = runTest {
        val first = TestRegistry()
        val controller = AndroidPermissionController(app).apply { attach(first) }
        val answer = async { controller.request(Capability.FINE_LOCATION) }
        runCurrent()
        val requestCode = first.launches.single().first
        // Rotation: the old registry saves its state, the Activity is destroyed, the new registry restores it.
        val saved = Bundle().also(first::onSaveInstanceState)
        controller.detach()
        runCurrent()
        assertFalse(answer.isCompleted, "detach alone must not complete a pending request")
        val second = TestRegistry().apply { onRestoreInstanceState(saved) }
        controller.attach(second)
        grant(fine, coarse)
        second.dispatchResult(requestCode, mapOf(fine to true, coarse to true))
        assertTrue(answer.await())
    }
}
