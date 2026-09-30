package com.packmuleforge.carfindermvp.shared.platform.android

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACTIVITY_RECOGNITION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.shared.platform.RequestMode
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A registry that answers permission requests from a script, the way the system dialog would.
 */
class ScriptedPermissionRegistry(
    private val granted: MutableSet<String>,
    private val willGrant: Set<String>,
) : ActivityResultRegistry() {
    val launches = mutableListOf<List<String>>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        @Suppress("UNCHECKED_CAST")
        val permissions = (input as Array<String>).toList()
        launches += permissions
        val result = permissions.associateWith { it in willGrant }
        granted += result.filterValues { it }.keys
        dispatchResult(requestCode, result)
    }
}

/**
 * Permission sequencing and status, through a scripted ActivityResultRegistry.
 * @requirement QR-004
 */
@RunWith(RobolectricTestRunner::class)
class AndroidPermissionControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val granted = mutableSetOf<String>()

    private fun controller(willGrant: Set<String>): Pair<AndroidPermissionController, ScriptedPermissionRegistry> {
        val registry = ScriptedPermissionRegistry(granted, willGrant)
        val controller = AndroidPermissionController(context, isGranted = { it in granted })
        controller.attach(registry)
        return controller to registry
    }

    private val allPermissions = setOf(
        ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, POST_NOTIFICATIONS, ACTIVITY_RECOGNITION,
        ACCESS_BACKGROUND_LOCATION,
    )

    /** @requirement FR-033 */
    @Test
    @Config(sdk = [36])
    fun request_sequencesForegroundThenNotificationsThenActivityThenBackground() = runTest {
        val (controller, registry) = controller(willGrant = allPermissions)
        val result = controller.request(Capability.entries.toSet())

        assertEquals(
            listOf(
                listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
                listOf(POST_NOTIFICATIONS),
                listOf(ACTIVITY_RECOGNITION),
                listOf(ACCESS_BACKGROUND_LOCATION),
            ),
            registry.launches,
        )
        assertTrue(result.values.all { it == CapabilityStatus.GRANTED }, "result was $result")
    }

    /** @requirement FR-033 */
    @Test
    @Config(sdk = [36])
    fun deniedForegroundLocation_stopsEarly_andReportsDenied() = runTest {
        val (controller, registry) = controller(willGrant = allPermissions - ACCESS_FINE_LOCATION - ACCESS_COARSE_LOCATION)
        val result = controller.request(Capability.entries.toSet())

        assertEquals(1, registry.launches.size)
        assertEquals(CapabilityStatus.DENIED, result[Capability.LOCATION_FOREGROUND])
        assertEquals(CapabilityStatus.DENIED, controller.status.value[Capability.LOCATION_FOREGROUND])
    }

    /** @requirement FR-033 */
    @Test
    @Config(sdk = [36])
    fun alreadyGrantedCapability_isNotPromptedAgain() = runTest {
        granted += listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
        val (controller, registry) = controller(willGrant = allPermissions)
        controller.request(setOf(Capability.LOCATION_FOREGROUND, Capability.NOTIFICATIONS))

        assertEquals(listOf(listOf(POST_NOTIFICATIONS)), registry.launches)
    }

    /** @requirement FR-033 */
    @Test
    @Config(sdk = [28])
    fun belowApi29And33_notificationsActivityAndBackgroundAreNotRequired() = runTest {
        val (controller, registry) = controller(willGrant = allPermissions)
        for (capability in listOf(Capability.NOTIFICATIONS, Capability.MOTION_ACTIVITY, Capability.LOCATION_BACKGROUND)) {
            assertEquals(RequestMode.NOT_REQUIRED, controller.requestMode(capability))
            assertEquals(CapabilityStatus.GRANTED, controller.status.value[capability])
        }
        controller.request(Capability.entries.toSet())
        assertEquals(listOf(listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)), registry.launches)
    }

    /** @requirement FR-033 */
    @Test
    @Config(sdk = [36])
    fun status_updatesAfterRequest() = runTest {
        val (controller, _) = controller(willGrant = setOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        assertEquals(CapabilityStatus.NOT_DETERMINED, controller.status.value[Capability.LOCATION_FOREGROUND])
        controller.request(setOf(Capability.LOCATION_FOREGROUND))
        assertEquals(CapabilityStatus.GRANTED, controller.status.value[Capability.LOCATION_FOREGROUND])
    }
}
