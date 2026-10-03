package com.packmuleforge.carfinder_mvp

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.platform.ActivityRecognizer
import com.packmuleforge.carfinder.shared.platform.AndroidActivityRecognizer
import com.packmuleforge.carfinder.shared.platform.AndroidClock
import com.packmuleforge.carfinder.shared.platform.AndroidHeadingProvider
import com.packmuleforge.carfinder.shared.platform.AndroidLocationProvider
import com.packmuleforge.carfinder.shared.platform.AndroidPermissionController
import com.packmuleforge.carfinder.shared.platform.Clock
import com.packmuleforge.carfinder.shared.platform.HeadingProvider
import com.packmuleforge.carfinder.shared.platform.LocationProvider
import com.packmuleforge.carfinder.shared.platform.PermissionController
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.state.ParkingStateMachine
import com.packmuleforge.carfinder_mvp.adapter.DataStoreParkedLocationRepository
import com.packmuleforge.carfinder_mvp.permission.PermissionFlowCoordinator
import com.packmuleforge.carfinder_mvp.service.ParkingDetectionService

/**
 * Application class that constructs and holds singleton instances of the dependency graph.
 * All major components are instantiated here as singletons for the app's lifetime.
 */
@Requirement("FR-010a", "FR-011", "FR-014")
class CarFinderApplication : Application() {
    // Singletons
    val repository: ParkedLocationRepository by lazy {
        DataStoreParkedLocationRepository.create(this)
    }

    val clock: Clock by lazy {
        AndroidClock()
    }

    val locationProvider: LocationProvider by lazy {
        AndroidLocationProvider(this)
    }

    val headingProvider: HeadingProvider by lazy {
        AndroidHeadingProvider(this)
    }

    val permissionController: PermissionController by lazy {
        AndroidPermissionController(this)
    }

    val activityRecognizer: ActivityRecognizer by lazy {
        AndroidActivityRecognizer(this)
    }

    val stateMachine: ParkingStateMachine by lazy {
        ParkingStateMachine(repository, clock)
    }

    // T110/T111 (CR-5 fix): sequences the FR-045 permission requests and is the sole trigger for
    // starting the service, exactly once, the moment location is granted (FR-046) — no unconditional
    // start here anymore, and no app restart is required.
    val permissionFlowCoordinator: PermissionFlowCoordinator by lazy {
        PermissionFlowCoordinator(
            permissionController = permissionController,
            onLocationGranted = ::startParkingService
        )
    }

    override fun onCreate() {
        super.onCreate()
        // T111 (CR-5 fix): the unconditional startForegroundService() that used to live here
        // called startForeground() with a "location" service type before location permission was
        // ever granted, throwing SecurityException on every fresh install. Starting the service is
        // now driven exclusively by permissionFlowCoordinator's onLocationGranted callback, invoked
        // from MainActivity once the user actually grants location (FR-045, FR-046).
    }

    /** FR-046: start the background parking service. Called only after location is granted. */
    fun startParkingService() {
        val serviceIntent = Intent(this, ParkingDetectionService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
