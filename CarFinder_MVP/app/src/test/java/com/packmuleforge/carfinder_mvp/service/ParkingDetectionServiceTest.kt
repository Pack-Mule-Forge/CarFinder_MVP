package com.packmuleforge.carfinder_mvp.service

import com.packmuleforge.carfinder.shared.annotation.Requirement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CR-13: onCreate() can be re-entered by Android's own START_STICKY restart after location
 * permission is revoked, independent of any app code path. Covers the extracted pure decision
 * that gates startForeground()/location monitoring on the permission check.
 */
@Requirement("FR-014")
class ParkingDetectionServiceTest {

    @Test
    fun shutsDownWhenLocationPermissionIsNotGranted() {
        assertTrue(requiresLocationPermissionShutdown(hasLocationPermission = false))
    }

    @Test
    fun proceedsWhenLocationPermissionIsGranted() {
        assertFalse(requiresLocationPermissionShutdown(hasLocationPermission = true))
    }
}
