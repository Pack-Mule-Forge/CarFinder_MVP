package com.packmuleforge.carfindermvp

import android.app.Application
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenPresenter
import com.packmuleforge.carfindermvp.shared.engine.ParkingEngine
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import com.packmuleforge.carfindermvp.shared.platform.createPlatformAdapters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app-scoped graph: one set of platform adapters and one [ParkingEngine] per process, shared by the
 * foreground service and the UI.
 *
 * @requirement FR-014, FR-033
 */
open class CarFinderApplication : Application() {

    /** Overridden in tests to supply fakes (TestCarFinderApplication). */
    open fun createAdapters(): PlatformAdapters = createPlatformAdapters(this)

    /** Overridden in tests so engine work runs synchronously. */
    protected open val engineDispatcher: CoroutineDispatcher = Dispatchers.Default

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + engineDispatcher) }

    val adapters: PlatformAdapters by lazy { createAdapters() }

    val engine: ParkingEngine by lazy { ParkingEngine(adapters, appScope) }

    val presenter: HomeScreenPresenter by lazy {
        HomeScreenPresenter(engine, adapters.heading, adapters.monotonicClock, appScope)
    }
}
