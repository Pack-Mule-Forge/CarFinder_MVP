package com.packmuleforge.carfindermvp

import android.Manifest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.ui.TestTags
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The home screen is the launch view: no navigation or taps are needed to see it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class MainActivityLaunchTest {

    private val app: TestCarFinderApplication = ApplicationProvider.getApplicationContext()

    init {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        // Fresh install: the fake store starts at the default FINDING record.
        app.fakes.permissions.set(Capability.LOCATION_FOREGROUND, CapabilityStatus.GRANTED)
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** @requirement FR-017 */
    @Test
    fun launch_showsDefaultViewWithoutUserAction() {
        compose.waitForIdle()
        compose.onNodeWithTag(TestTags.STATUS_MESSAGE).assertTextEquals("Parked location unavailable.")
    }

    /** @requirement FR-033 */
    @Test
    fun resumeWithLocationGranted_startsDetectionService() {
        compose.waitForIdle()
        val started = generateSequence { shadowOf(app).nextStartedService }.toList()
        assertEquals(true, started.any { it.component?.className == ParkingDetectionService::class.java.name })
    }
}
