package com.packmuleforge.carfindermvp.service

import android.app.Notification
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.TestCarFinderApplication
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class DetectionNotificationTest {

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    /** @requirement FR-051 */
    @Test
    fun withBackgroundLocationTheNotificationSaysMonitoringForParking() {
        val notification = DetectionNotification.build(app, hasBackgroundLocation = true)
        assertEquals("Car Finder", notification.title())
        assertEquals("Monitoring for parking", notification.text())
    }

    /** @requirement FR-054 */
    @Test
    fun withoutBackgroundLocationItAsksForAllowAllTheTimeAndOpensTheAppsSettings() {
        val notification = DetectionNotification.build(app, hasBackgroundLocation = false)
        assertEquals("Car Finder", notification.title())
        assertTrue(notification.text().contains("Allow all the time"), notification.text())
        val opened = shadowOf(notification.contentIntent).savedIntent
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.action)
        assertEquals(Uri.fromParts("package", app.packageName, null), opened.data)
    }
}
