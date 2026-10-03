package com.packmuleforge.carfindermvp.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class BootReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()

    private fun boot(action: String = Intent.ACTION_BOOT_COMPLETED) {
        BootReceiver().onReceive(app, Intent(action))
    }

    private fun startedService() = shadowOf(app).nextStartedService

    /** @requirement FR-053 */
    @Test
    fun withBackgroundLocationAndBothRequiredPermissionsBootStartsTheService() {
        boot()
        assertEquals(ParkingDetectionService::class.java.name, startedService()?.component?.className)
    }

    /** @requirement FR-054 */
    @Test
    fun withOnlyForegroundLocationBootStartsNothing() {
        app.platform.permissions.setStatus(Capability.BACKGROUND_LOCATION, PermissionStatus.DENIED)
        boot()
        assertNull(startedService())
    }

    /** @requirement FR-052 */
    @Test
    fun withNoLocationBootStartsNothing() {
        app.platform.permissions.setStatus(Capability.FINE_LOCATION, PermissionStatus.DENIED)
        app.platform.permissions.setStatus(Capability.BACKGROUND_LOCATION, PermissionStatus.DENIED)
        boot()
        assertNull(startedService())
    }

    /** @requirement FR-052 */
    @Test
    fun withNotificationsNotGrantedBootStartsNothing() {
        app.platform.permissions.setStatus(Capability.NOTIFICATIONS, PermissionStatus.DENIED)
        boot()
        assertNull(startedService())
    }

    /** @requirement FR-052 */
    @Test
    fun anUnrelatedActionDoesNothing() {
        boot(Intent.ACTION_TIME_CHANGED)
        assertNull(startedService())
    }
}
