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

/** Builds the platform adapters and the one application-scoped engine. Tests override the adapters. */
open class CarFinderApplication : Application() {

    open val engineDispatcher: CoroutineDispatcher get() = Dispatchers.Default

    val adapters: PlatformAdapters by lazy { createAdapters() }

    val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + engineDispatcher) }

    val engine: ParkingEngine by lazy { ParkingEngine(adapters, applicationScope) }

    val presenter: HomeScreenPresenter by lazy { HomeScreenPresenter(engine, adapters, applicationScope) }

    open fun createAdapters(): PlatformAdapters = createPlatformAdapters(this)
}
