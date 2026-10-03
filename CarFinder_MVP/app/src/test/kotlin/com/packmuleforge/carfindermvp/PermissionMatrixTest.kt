package com.packmuleforge.carfindermvp

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.service.BootReceiver
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every combination of granted and denied across the four capabilities (SC-011).
 *
 * @requirement QR-003
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class PermissionMatrixTest {

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()

    /** @requirement FR-049, FR-052 */
    @Test
    fun noCombinationCrashesAndSensingStartsOnlyWithBothRequiredPermissions() {
        val failures = mutableListOf<String>()
        val combinations = (0 until 16).map { bits -> Capability.entries.associateWith { (bits shr it.ordinal) and 1 == 1 } }
        for (granted in combinations) {
            val label = granted.entries.joinToString { "${it.key}=${it.value}" }
            granted.forEach { (capability, isGranted) ->
                app.platform.permissions.setStatus(capability, if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED)
                // A denied required permission is asked for again and denied again.
                if (!isGranted) app.platform.permissions.enqueueAnswers(capability, false)
            }
            val required = granted.getValue(Capability.FINE_LOCATION) && granted.getValue(Capability.NOTIFICATIONS)
            val startsBefore = app.platform.location.startCount
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    shadowOf(Looper.getMainLooper()).idle()
                    val shown = app.presenter.state.value
                    if (!required && shown !is HomeScreenState.PermissionRequired) failures += "$label: showed $shown"
                    if (it.state == androidx.lifecycle.Lifecycle.State.DESTROYED) failures += "$label: closed on its own"
                }
                BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
                val service = Robolectric.buildService(ParkingDetectionService::class.java).create()
                service.get().onStartCommand(null, 0, 1)
                service.destroy()
            } catch (e: Exception) {
                failures += "$label: threw $e"
            }
            val started = app.platform.location.startCount > startsBefore
            if (started != required) failures += "$label: location started=$started"
        }
        assertEquals(emptyList(), failures)
    }
}
