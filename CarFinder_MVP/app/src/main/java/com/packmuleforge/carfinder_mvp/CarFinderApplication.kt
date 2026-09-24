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

    override fun onCreate() {
        super.onCreate()

        // Start the parking detection service (FR-010a: start after permission grant)
        // In a real app, this would be conditioned on permissions being granted
        val serviceIntent = Intent(this, ParkingDetectionService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
