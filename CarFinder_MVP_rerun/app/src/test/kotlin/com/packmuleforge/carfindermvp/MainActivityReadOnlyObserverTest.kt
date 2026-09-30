package com.packmuleforge.carfindermvp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.Readings
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Activity is a read-only observer: it shows the persisted state but never starts detection itself.
 * Robolectric records ParkingDetectionService's start intent without running it, so anything started here was
 * started by the Activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCarFinderApplication::class)
class MainActivityReadOnlyObserverTest {

    private val app: TestCarFinderApplication = ApplicationProvider.getApplicationContext()

    init {
        app.fakes.store.seed(
            PersistedParkingRecord(
                state = LifecycleState.PARKED,
                parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
            ),
        )
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** @requirement FR-014, FR-033 */
    @Test
    fun launch_restoresStateForDisplay_withoutStartingSamplingSubscriptionsOrTheActor() {
        compose.waitForIdle()

        assertEquals(LifecycleState.PARKED, app.engine.state.value.lifecycle, "persisted state is restored for display")
        assertFalse(app.engine.isRunning, "the actor loop is started only by the service")
        assertFalse(app.fakes.location.isStarted, "location sampling is started only by the service")
        assertFalse(app.fakes.activity.isStarted, "activity recognition is started only by the service")
        assertTrue(app.fakes.location.profileHistory.isEmpty(), "no sampling profile was requested")
        assertTrue(app.fakes.store.writes.isEmpty(), "the Activity never writes the store")
    }
}
