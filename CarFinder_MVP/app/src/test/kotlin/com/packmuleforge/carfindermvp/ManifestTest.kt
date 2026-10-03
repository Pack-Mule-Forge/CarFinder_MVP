package com.packmuleforge.carfindermvp

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.service.BootReceiver
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class ManifestTest {

    private val app = ApplicationProvider.getApplicationContext<TestCarFinderApplication>()
    private val packageManager = app.packageManager

    /** @requirement FR-047 */
    @Test
    fun theManifestRequestsExactlyTheNeededPermissions() {
        val requested = packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        val needed = setOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
        )
        assertTrue(requested.containsAll(needed), "missing: ${needed - requested}")
    }

    /** @requirement FR-051 */
    @Test
    fun theServiceIsALocationForegroundServiceAndNotExported() {
        val service = packageManager.getServiceInfo(ComponentName(app, ParkingDetectionService::class.java), 0)
        assertTrue(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        assertFalse(service.exported)
    }

    /** @requirement FR-053 */
    @Test
    fun theReceiverListensForBootCompleted() {
        val receivers = packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName), 0)
        assertTrue(receivers.any { it.activityInfo.name == BootReceiver::class.java.name })
    }

    /** @requirement QR-015 */
    @Test
    fun theMinimumSdkIsAndroid8() {
        assertEquals(26, app.applicationInfo.minSdkVersion)
    }
}
