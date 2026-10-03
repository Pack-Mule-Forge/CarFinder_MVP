package com.packmuleforge.carfindermvp.service

import android.app.Service
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class ParkingDetectionServiceTest {

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()
    private val intent = Intent(app, ParkingDetectionService::class.java)

    /** @requirement FR-051, FR-052 */
    @Test
    fun withBothRequiredPermissionsAStartCommandPostsTheNotificationAndStartsTheEngine() {
        val controller = Robolectric.buildService(ParkingDetectionService::class.java, intent).create()
        val result = controller.get().onStartCommand(intent, 0, 1)
        assertEquals(Service.START_STICKY, result)
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertTrue(app.engine.isRunning)
        assertTrue(app.platform.location.isStarted)
    }

    /** @requirement FR-049, FR-050, FR-052 */
    @Test
    fun withEitherRequiredPermissionMissingTheServiceStopsAndStartsNothing() {
        for (missing in listOf(Capability.FINE_LOCATION, Capability.NOTIFICATIONS)) {
            app.platform.permissions.setStatus(Capability.FINE_LOCATION, PermissionStatus.GRANTED)
            app.platform.permissions.setStatus(Capability.NOTIFICATIONS, PermissionStatus.GRANTED)
            app.platform.permissions.setStatus(missing, PermissionStatus.DENIED)
            val controller = Robolectric.buildService(ParkingDetectionService::class.java, intent).create()
            controller.get().onStartCommand(intent, 0, 1)
            assertTrue(shadowOf(controller.get()).isStoppedBySelf, "$missing")
            assertNull(shadowOf(controller.get()).lastForegroundNotification)
            assertFalse(app.platform.location.isStarted)
            assertFalse(app.engine.isRunning)
        }
    }

    /** @requirement FR-050 */
    @Test
    fun aStickyRestartWithARequiredPermissionSinceRevokedStopsTheService() {
        val controller = Robolectric.buildService(ParkingDetectionService::class.java, intent).create()
        controller.get().onStartCommand(intent, 0, 1)
        app.platform.permissions.setStatus(Capability.NOTIFICATIONS, PermissionStatus.DENIED)
        controller.get().onStartCommand(null, 0, 2)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    /** @requirement FR-050 */
    @Test
    fun theServiceChecksPermissionsItselfOnEveryStart() {
        val controller = Robolectric.buildService(ParkingDetectionService::class.java, intent).create()
        val before = app.platform.permissions.refreshCount
        controller.get().onStartCommand(null, 0, 1)
        assertTrue(app.platform.permissions.refreshCount > before)
    }

    /** @requirement FR-051 */
    @Test
    fun onDestroyStopsTheEngine() {
        val controller = Robolectric.buildService(ParkingDetectionService::class.java, intent).create()
        controller.get().onStartCommand(intent, 0, 1)
        controller.destroy()
        assertFalse(app.engine.isRunning)
        assertFalse(app.platform.location.isStarted)
    }
}
