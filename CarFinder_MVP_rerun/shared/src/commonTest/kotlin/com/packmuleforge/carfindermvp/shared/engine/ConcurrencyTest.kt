package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.testing.FakePlatform
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Inputs from several threads at once are serialized by the engine's actor (research R7): every published state
 * and every persisted record keeps "a location is held if and only if PARKED".
 * @requirement QR-001
 */
class ConcurrencyTest {

    /** @requirement FR-018 */
    @Test
    fun interleavedInputs_keepTheParkedInvariant_andSerializeWrites() = runTest {
        val platform = FakePlatform()
        val violations = MutableStateFlow(emptyList<String>())
        withContext(Dispatchers.Default) {
            val engineScope = this
            val engine = ParkingEngine(platform.adapters, engineScope)
            val watcher = launch {
                engine.state.collect { s ->
                    if ((s.lifecycle == LifecycleState.PARKED) != (s.parkedLocation != null)) {
                        violations.update { it + "state $s" }
                    }
                }
            }
            engine.start()
            val radius = CarFinderConstants.CONVERGENCE_RADIUS_METERS
            val producers = List(4) { producer ->
                async {
                    repeat(200) { i ->
                        when ((i + producer) % 4) {
                            0 -> platform.location.emit(Readings.readingOffset(radius * 3 * i, 0.0, speedMph = Readings.DRIVING_MPH))
                            1 -> platform.location.emit(Readings.readingOffset(0.0, radius * 0.05 * (i % 3), speedMph = Readings.PARKED_MPH))
                            2 -> platform.activity.emitInVehicle()
                            else -> engine.setGuidanceVisible(i % 2 == 0)
                        }
                        yield()
                    }
                }
            }
            producers.awaitAll()
            withTimeout(timeMillis = 10_000L) { engine.state.first() }
            watcher.cancel()
            engine.stop()
        }
        assertTrue(violations.value.isEmpty(), violations.value.joinToString("\n"))
        val broken = platform.store.writes.filter { (it.state == LifecycleState.PARKED) != (it.parkedLocation != null) }
        assertTrue(broken.isEmpty(), "records breaking the invariant: $broken")
        assertTrue(platform.store.writes.isNotEmpty(), "the interleaved stream should have produced transitions")
    }
}
