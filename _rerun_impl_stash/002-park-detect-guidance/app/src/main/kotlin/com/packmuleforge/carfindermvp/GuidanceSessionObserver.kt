package com.packmuleforge.carfindermvp

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.packmuleforge.carfindermvp.shared.engine.ParkingEngine
import com.packmuleforge.carfindermvp.shared.platform.HeadingSource

/**
 * Turns on the 1 s guidance sampling profile and the compass only while the home screen is visible (research R2,
 * R3). This side effect lives outside composition so every Composable stays pure (Constitution IV).
 *
 * @requirement FR-017
 */
class GuidanceSessionObserver(
    private val engine: ParkingEngine,
    private val heading: HeadingSource,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        engine.setGuidanceVisible(true)
        heading.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        engine.setGuidanceVisible(false)
        heading.stop()
    }
}
