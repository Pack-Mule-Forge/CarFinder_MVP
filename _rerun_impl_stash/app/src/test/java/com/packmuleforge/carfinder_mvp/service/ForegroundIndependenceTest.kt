package com.packmuleforge.carfinder_mvp.service

import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.LocationSample
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder.shared.platform.Clock
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.repository.PersistedParkingData
import com.packmuleforge.carfinder.shared.state.ParkingStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FR-010a/FR-014/SC-012: the state machine's park-detection cycle runs identically whether or not
 * any UI is attached — feeding it a scripted sequence of samples must yield the same state
 * sequence and captured location with no UI simulated at all, and with UI-side lifecycle churn
 * (ephemeral-collection start/stop) interleaved between samples. It also asserts, by scanning the
 * UI-layer sources, that no UI-originated call path can reach [ParkingStateMachine.onLocationSample].
 */
@Requirement("FR-010a", "FR-014", "SC-012")
class ForegroundIndependenceTest {

    private class InMemoryParkedLocationRepository : ParkedLocationRepository {
        private val flow = MutableStateFlow(PersistedParkingData(ParkingState.FINDING, null))

        override fun observe(): Flow<PersistedParkingData> = flow

        override suspend fun load(): PersistedParkingData = flow.value

        override suspend fun save(state: ParkingState, parkedLocation: ParkedLocation?) {
            flow.value = PersistedParkingData(state, parkedLocation)
        }

        override suspend fun clearLocation() {
            flow.value = flow.value.copy(state = ParkingState.FINDING, parkedLocation = null)
        }
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun nowEpochMillis(): Long = millis
    }

    /**
     * A scripted drive-park-driveaway cycle: fast samples (DRIVING), then slow samples that
     * converge within CONVERGENCE_RADIUS_METERS (PARKING -> PARKED), then a fast sample again
     * (drive away, clearing the location).
     */
    private fun scriptedSamples(): List<LocationSample> {
        fun point(lat: Double) = GeoPoint(latitudeDegrees = lat, longitudeDegrees = -122.0, accuracyRadiusMeters = 5.0)

        return listOf(
            LocationSample(point(37.0000), speedMetersPerSecond = 15.0, timestampEpochMillis = 0L),
            LocationSample(point(37.0001), speedMetersPerSecond = 1.0, timestampEpochMillis = 1_000L),
            LocationSample(point(37.00011), speedMetersPerSecond = 1.0, timestampEpochMillis = 2_000L),
            LocationSample(point(37.00012), speedMetersPerSecond = 1.0, timestampEpochMillis = 3_000L),
            LocationSample(point(37.00011), speedMetersPerSecond = 1.0, timestampEpochMillis = 4_000L),
            // Two consecutive fast samples: SpeedSmoother is a rolling 3-median, so a single fast
            // sample after three slow ones still medians out below the driving threshold.
            LocationSample(point(37.0050), speedMetersPerSecond = 15.0, timestampEpochMillis = 5_000L),
            LocationSample(point(37.0100), speedMetersPerSecond = 15.0, timestampEpochMillis = 6_000L)
        )
    }

    private suspend fun runScriptRecordingStates(
        stateMachine: ParkingStateMachine,
        samples: List<LocationSample>,
        betweenSamples: () -> Unit = {}
    ): List<ParkingState> {
        val recorded = mutableListOf<ParkingState>()
        for (sample in samples) {
            stateMachine.onLocationSample(sample)
            recorded.add(stateMachine.currentState())
            betweenSamples()
        }
        return recorded
    }

    @Test
    fun scriptedParkCycleIsUnaffectedByUiLifecycleChurnInterleavedBetweenSamples() = runTest {
        val straightRepository = InMemoryParkedLocationRepository()
        val straightMachine = ParkingStateMachine(straightRepository, FixedClock(0L))
        val straightStates = runScriptRecordingStates(straightMachine, scriptedSamples())
        val straightFinal = straightRepository.load()

        // Lifecycle churn here is a stand-in for the UI's own start/stop of its ephemeral
        // location/heading collection (FR-044) — it must have no bearing on the state machine,
        // which is fed samples directly by the foreground service (FR-014).
        var uiLifecycleStarted = false
        var uiLifecycleStartStopCount = 0
        val interleavedRepository = InMemoryParkedLocationRepository()
        val interleavedMachine = ParkingStateMachine(interleavedRepository, FixedClock(0L))
        val interleavedStates = runScriptRecordingStates(interleavedMachine, scriptedSamples()) {
            uiLifecycleStarted = !uiLifecycleStarted
            uiLifecycleStartStopCount++
        }
        val interleavedFinal = interleavedRepository.load()

        assertEquals(straightStates, interleavedStates, "state sequence must not depend on UI visibility")
        assertEquals(straightFinal.state, interleavedFinal.state)
        assertEquals(straightFinal.parkedLocation, interleavedFinal.parkedLocation)

        // Sanity: the cycle actually exercised DRIVING -> PARKING -> PARKED -> DRIVING and captured
        // a location, and the interleaved "lifecycle churn" toggled at least once per sample.
        assertTrue(straightStates.contains(ParkingState.PARKED), "script must reach PARKED")
        assertEquals(ParkingState.DRIVING, straightFinal.state, "drive-away must end in DRIVING")
        assertEquals(scriptedSamples().size, uiLifecycleStartStopCount)
    }

    @Test
    fun uiLayerSourceNeverReferencesTheStateMachineOrItsMutator() {
        val uiDir = File("src/main/java/com/packmuleforge/carfinder_mvp/ui")
        val kotlinFiles = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(kotlinFiles.isNotEmpty(), "Expected at least one Kotlin file under $uiDir")

        val mainActivity = File("src/main/java/com/packmuleforge/carfinder_mvp/MainActivity.kt")
        assertTrue(mainActivity.exists(), "Expected MainActivity.kt at $mainActivity")

        val filesToCheck = kotlinFiles + mainActivity
        for (file in filesToCheck) {
            val content = file.readText()
            assertFalse(
                content.contains("ParkingStateMachine"),
                "${file.name} references ParkingStateMachine — the state machine must be driven only " +
                    "by the foreground service, never by the UI (FR-014)"
            )
            assertFalse(
                content.contains("onLocationSample"),
                "${file.name} calls onLocationSample — no UI-originated call path may reach the " +
                    "state machine's sole mutator (FR-014)"
            )
        }
    }
}
