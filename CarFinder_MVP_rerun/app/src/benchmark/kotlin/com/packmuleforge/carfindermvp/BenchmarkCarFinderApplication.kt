package com.packmuleforge.carfindermvp

import android.os.SystemClock
import com.packmuleforge.carfindermvp.shared.domain.HeadingReading
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.CapabilityStatus
import com.packmuleforge.carfindermvp.shared.platform.PlatformAdapters
import com.packmuleforge.carfindermvp.shared.testing.FakePermissionController
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Benchmark-only application: a seeded PARKED record and synthetic fix and heading feeds, so the guidance display
 * animates continuously for the FR-027 frame-timing benchmark. Exists only in the benchmark build type.
 */
class BenchmarkCarFinderApplication : CarFinderApplication() {

    private val fakes = FakePlatform(
        // No real location permission is involved: report it denied so the foreground service is never started.
        permissions = FakePermissionController(
            initial = Capability.entries.associateWith { CapabilityStatus.DENIED },
            grantOnRequest = emptySet(),
        ),
    )

    override fun createAdapters(): PlatformAdapters = fakes.adapters

    override fun onCreate() {
        super.onCreate()
        fakes.store.seed(
            PersistedParkingRecord(
                state = LifecycleState.PARKED,
                parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 0L),
            ),
        )
        appScope.launch { feedHeadings() }
        appScope.launch { feedFixes() }
    }

    /** A slowly turning compass at roughly the SENSOR_DELAY_UI rate (about 15 events per second). */
    private suspend fun feedHeadings() {
        var degrees = 0.0
        while (appScope.isActive) {
            val now = SystemClock.elapsedRealtime()
            fakes.monotonicClock.now = now
            fakes.heading.emit(HeadingReading(degrees, now))
            degrees = (degrees + 1.5) % 360.0
            delay(HEADING_PERIOD_MILLIS)
        }
    }

    /** A walker circling the car at the guidance sampling rate (one fix per second). */
    private suspend fun feedFixes() {
        var step = 0
        while (appScope.isActive) {
            val now = SystemClock.elapsedRealtime()
            fakes.monotonicClock.now = now
            val angle = step * 0.1
            fakes.location.emit(
                Readings.readingOffset(
                    northMeters = WALK_RADIUS_METERS * kotlin.math.cos(angle),
                    eastMeters = WALK_RADIUS_METERS * kotlin.math.sin(angle),
                    speedMph = null,
                    elapsedMillis = now,
                ),
            )
            step++
            delay(FIX_PERIOD_MILLIS)
        }
    }

    private companion object {
        const val HEADING_PERIOD_MILLIS = 66L
        const val FIX_PERIOD_MILLIS = 1_000L
        const val WALK_RADIUS_METERS = 80.0
    }
}
