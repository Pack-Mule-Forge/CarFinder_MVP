package com.packmuleforge.carfindermvp.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Restart after reboot or app update, only when background location allows it (FR-033, research R5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class BootReceiverTest {

    private val app: TestCarFinderApplication = ApplicationProvider.getApplicationContext()

    private fun grant(foreground: Boolean, background: Boolean) {
        val status = { granted: Boolean -> if (granted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED }
        app.fakes.permissions.set(Capability.LOCATION_FOREGROUND, status(foreground))
        app.fakes.permissions.set(Capability.LOCATION_BACKGROUND, status(background))
    }

    private fun receive(action: String) = BootReceiver().onReceive(app, Intent(action))

    private fun nextStartedService() = shadowOf(app).nextStartedService

    /** @requirement FR-033 */
    @Test
    fun bootCompleted_withBothPermissions_startsService() {
        grant(foreground = true, background = true)
        receive(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(ParkingDetectionService::class.java.name, nextStartedService()?.component?.className)
    }

    /** @requirement FR-033 */
    @Test
    fun packageReplaced_withBothPermissions_startsService() {
        grant(foreground = true, background = true)
        receive(Intent.ACTION_MY_PACKAGE_REPLACED)
        assertEquals(ParkingDetectionService::class.java.name, nextStartedService()?.component?.className)
    }

    /** @requirement FR-033 */
    @Test
    fun bootCompleted_withForegroundOnly_doesNotStartService() {
        grant(foreground = true, background = false)
        receive(Intent.ACTION_BOOT_COMPLETED)
        assertNull(nextStartedService())
    }

    /** @requirement FR-033 */
    @Test
    fun bootCompleted_withoutLocation_doesNotStartService() {
        grant(foreground = false, background = false)
        receive(Intent.ACTION_BOOT_COMPLETED)
        assertNull(nextStartedService())
    }

    /** @requirement FR-033 */
    @Test
    fun otherActions_areIgnored() {
        grant(foreground = true, background = true)
        receive(Intent.ACTION_TIMEZONE_CHANGED)
        assertNull(nextStartedService())
    }
}
