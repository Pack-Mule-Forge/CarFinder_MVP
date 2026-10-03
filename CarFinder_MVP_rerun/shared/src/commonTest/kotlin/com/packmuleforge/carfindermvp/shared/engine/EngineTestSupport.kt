package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SPEED_FILTER_WINDOW_SIZE
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** Shared helpers for engine and presenter tests. */
internal object EngineTestSupport {
    /** A reading that carries only a speed, so it never enters a convergence window. */
    fun speedOnly(mph: Double?, receivedElapsedMillis: Long = 0) =
        Readings.readingAt(accuracyMeters = null, speedMph = mph, receivedElapsedMillis = receivedElapsedMillis)

    fun speeds(mph: Double, count: Int = SPEED_FILTER_WINDOW_SIZE) = List(count) { speedOnly(mph) }

    /** A Parked Location declared long before the fake wall clock's start, so its recovery window is closed. */
    fun oldParkedLocation(northMeters: Double = 0.0) = Readings.readingOffset(northMeters, 0.0).let {
        ParkedLocation(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyMeters = Readings.GOOD_ACCURACY_METERS,
            declaredAtEpochMillis = FakeWallClock.DEFAULT_START_EPOCH_MILLIS - PARKED_RECOVERY_WINDOW_MILLIS * 10,
        )
    }

    fun parkedRecord(location: ParkedLocation = oldParkedLocation()) =
        PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)
}

/** Emits each reading through the fake location source and lets the engine process it. */
internal suspend fun TestScope.send(platform: FakePlatform, readings: List<LocationReading>) {
    for (reading in readings) {
        platform.location.emit(reading.copy(receivedElapsedMillis = platform.monotonicClock.elapsedRealtimeMillis()))
        runCurrent()
    }
}

internal suspend fun TestScope.send(platform: FakePlatform, vararg readings: LocationReading) =
    send(platform, readings.toList())
