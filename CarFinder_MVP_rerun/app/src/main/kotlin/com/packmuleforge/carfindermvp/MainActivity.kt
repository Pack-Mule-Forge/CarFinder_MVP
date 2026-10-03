package com.packmuleforge.carfindermvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.packmuleforge.carfindermvp.ui.HomeScreen
import kotlinx.coroutines.launch

/**
 * Shows the presenter's state with no user action. A read-only observer: it restores the stored state for display
 * and never starts the engine.
 *
 * @requirement FR-043, QR-013
 */
class MainActivity : ComponentActivity() {

    private val app: CarFinderApplication get() = application as CarFinderApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { app.engine.restore() }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = app.presenter.onGuidanceVisible(true)
                override fun onStop(owner: LifecycleOwner) = app.presenter.onGuidanceVisible(false)
            },
        )
        setContent {
            val state by app.presenter.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onArrivalAnswered = app.presenter::onArrivalAnswered,
                onDenialConfirmed = {},
                onDenialDismissed = {},
            )
        }
    }
}
