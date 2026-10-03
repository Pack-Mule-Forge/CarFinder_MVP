package com.packmuleforge.carfindermvp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.packmuleforge.carfindermvp.service.ParkingDetectionService
import com.packmuleforge.carfindermvp.shared.platform.android.AndroidPermissionController
import com.packmuleforge.carfindermvp.ui.HomeScreen
import kotlinx.coroutines.launch

/**
 * Shows the presenter's state with no user action and is otherwise a read-only observer: it never starts the
 * engine. On every start it re-reads permissions and runs the request sequence; when both required permissions
 * are granted it starts the detection service; when the presenter raises a close it stops the service and closes.
 *
 * @requirement FR-043, FR-048, FR-049, FR-054, FR-056, QR-013
 */
class MainActivity : ComponentActivity() {

    private val app: CarFinderApplication get() = application as CarFinderApplication
    private val permissionController get() = app.adapters.permissions as? AndroidPermissionController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionController?.attach(activityResultRegistry)
        lifecycleScope.launch { app.engine.restore() }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    app.presenter.onGuidanceVisible(true)
                    app.adapters.permissions.refresh()
                    app.presenter.runPermissionSequence()
                }

                override fun onStop(owner: LifecycleOwner) = app.presenter.onGuidanceVisible(false)
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.adapters.permissions.status.collect { if (it.areRequiredGranted) startDetection() }
                }
                launch {
                    app.presenter.isClosePending.collect { if (it) closeApp() }
                }
            }
        }
        setContent {
            val state by app.presenter.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onArrivalAnswered = app.presenter::onArrivalAnswered,
                onDenialConfirmed = app.presenter::onDenialConfirmed,
                onDenialDismissed = app.presenter::onDenialDismissed,
            )
        }
    }

    override fun onDestroy() {
        // Leaving the screen for good abandons any pending prompt or confirmation; a rotation does not.
        if (isFinishing && !isChangingConfigurations) app.presenter.cancelPermissionSequence()
        permissionController?.detach()
        super.onDestroy()
    }

    private fun startDetection() {
        startForegroundService(Intent(this, ParkingDetectionService::class.java))
    }

    private fun closeApp() {
        stopService(Intent(this, ParkingDetectionService::class.java))
        finishAndRemoveTask()
        app.presenter.onClosed()
    }
}
