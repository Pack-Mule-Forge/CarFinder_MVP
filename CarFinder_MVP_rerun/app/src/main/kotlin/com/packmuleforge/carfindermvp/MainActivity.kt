package com.packmuleforge.carfindermvp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidPermissionController
import com.packmuleforge.carfindermvp.ui.HomeScreen
import com.packmuleforge.carfindermvp.ui.theme.CarFinderTheme
import kotlinx.coroutines.launch

/**
 * Requests permissions, starts always-on detection once location is granted, and shows the home screen as the
 * launch view. The presenter's state is copied into [uiState] here, outside composition, so HomeScreen is a pure
 * function of the value passed to it (Constitution IV).
 *
 * @requirement FR-017, FR-033
 */
class MainActivity : ComponentActivity() {

    private val app get() = application as CarFinderApplication

    // Fakes in tests have no Activity binding; only the Android controller needs one.
    private val androidPermissions get() = app.adapters.permissions as? AndroidPermissionController

    /** Mirrors presenter.state while STARTED; never computed here. */
    private var uiState by mutableStateOf<HomeScreenState>(HomeScreenState.Unavailable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidPermissions?.attach(this)
        // The UI is a read-only observer: restore() only reads the persisted state for display. Starting detection
        // (actor loop, location sampling, activity recognition) belongs to ParkingDetectionService alone.
        lifecycleScope.launch { app.engine.restore() }
        lifecycle.addObserver(GuidanceSessionObserver(app.engine, app.adapters.heading))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { app.presenter.state.collect { uiState = it } }
        }
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                app.adapters.permissions.request(Capability.entries.toSet())
                startDetectionIfPermitted()
            }
        }
        setContent { CarFinderTheme { HomeScreen(uiState, app.presenter::onArrivalAnswered) } }
    }

    override fun onResume() {
        super.onResume()
        // Picks up a permission granted later in system Settings (spec Edge Cases).
        androidPermissions?.refresh()
        startDetectionIfPermitted()
    }

    override fun onDestroy() {
        androidPermissions?.detach()
        super.onDestroy()
    }

    /** Starting an already-running service only re-delivers onStartCommand, so this is safe to repeat. */
    private fun startDetectionIfPermitted() {
        if (app.adapters.permissions.status.value[Capability.LOCATION_FOREGROUND] == CapabilityStatus.GRANTED) {
            ContextCompat.startForegroundService(this, Intent(this, ParkingDetectionService::class.java))
        }
    }
}
