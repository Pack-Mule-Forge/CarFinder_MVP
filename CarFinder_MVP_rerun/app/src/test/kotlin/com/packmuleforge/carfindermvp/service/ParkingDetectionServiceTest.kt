package com.packmuleforge.carfindermvp.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.R
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.shared.testing.Readings
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The always-on foreground service that hosts the engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class ParkingDetectionServiceTest {

    private val app: TestCarFinderApplication = ApplicationProvider.getApplicationContext()

    private fun startService(): Pair<ParkingDetectionService, Int> {
        val controller = Robolectric.buildService(ParkingDetectionService::class.java).create()
        val service = controller.get()
        val result = service.onStartCommand(Intent(app, ParkingDetectionService::class.java), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return service to result
    }

    private fun Service.foregroundNotification(): Notification = assertNotNull(shadowOf(this).lastForegroundNotification)

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun grantLocation(background: Boolean) {
        app.fakes.permissions.set(Capability.LOCATION_FOREGROUND, CapabilityStatus.GRANTED)
        app.fakes.permissions.set(
            Capability.LOCATION_BACKGROUND,
            if (background) CapabilityStatus.GRANTED else CapabilityStatus.DENIED,
        )
    }

    /** @requirement FR-033 */
    @Test
    fun onStartCommand_startsForeground_onDetectionChannel() {
        grantLocation(background = true)
        val (service, _) = startService()
        assertEquals(DetectionNotification.CHANNEL_ID, service.foregroundNotification().channelId)
    }

    /** @requirement FR-033 */
    @Test
    fun onStartCommand_returnsStartSticky() {
        grantLocation(background = true)
        assertEquals(Service.START_STICKY, startService().second)
    }

    /** @requirement FR-033 */
    @Test
    fun onStartCommand_startsTheEngine() {
        grantLocation(background = true)
        startService()
        assertTrue(app.fakes.location.isStarted)
    }

    /** @requirement FR-033 */
    @Test
    fun notificationText_reflectsLifecycle() {
        grantLocation(background = true)
        app.fakes.store.seed(
            PersistedParkingRecord(
                state = LifecycleState.PARKED,
                parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
            ),
        )
        val (service, _) = startService()
        val latest = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(DetectionNotification.NOTIFICATION_ID) ?: service.foregroundNotification()
        assertEquals(app.getString(R.string.notification_parked), latest.text())
    }

    /** @requirement FR-033 */
    @Test
    fun foregroundOnlyPermission_notificationAsksForAllTheTime_andOpensAppSettings() {
        grantLocation(background = false)
        val (service, _) = startService()
        val latest = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(DetectionNotification.NOTIFICATION_ID) ?: service.foregroundNotification()

        assertEquals(app.getString(R.string.notification_needs_background), latest.text())
        val intent = shadowOf(latest.contentIntent).savedIntent
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.fromParts("package", app.packageName, null), intent.data)
    }

    /** @requirement FR-033 */
    @Test
    fun bothPermissionsGranted_notificationShowsLifecycleText() {
        grantLocation(background = true)
        val (service, _) = startService()
        val latest = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(DetectionNotification.NOTIFICATION_ID) ?: service.foregroundNotification()
        assertEquals(app.getString(R.string.status_unavailable), latest.text())
    }
}
