package com.packmuleforge.carfindermvp.shared.engine

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.CONE_CONTAINMENT_TARGET
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.SAMPLING_INTERVAL_GUIDANCE_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS
import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.FakeWallClock
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.shared.testing.ReplayRunner
import com.packmuleforge.carfindermvp.shared.testing.ReplayScript
import com.packmuleforge.carfindermvp.shared.testing.ReplayStep
import com.packmuleforge.carfindermvp.shared.testing.SeededError
import com.packmuleforge.carfindermvp.shared.testing.TruePosition
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guidance played through the real engine and presenter on a virtual clock (SC-006, SC-007, SC-008).
 *
 * @requirement QR-016
 */
class GuidanceReplayTest {

    private val trueCar = TruePosition(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE)
    private val parkedAccuracy = Readings.GOOD_ACCURACY_METERS * 2
    private val fixAccuracy = Readings.GOOD_ACCURACY_METERS * 2

    private fun position(northMeters: Double, eastMeters: Double): TruePosition =
        Readings.readingOffset(northMeters, eastMeters).let { TruePosition(it.latitude, it.longitude) }

    /** The stored location is the true car displaced by a seeded error no larger than its accuracy (QR-016). */
    private fun parkedRecord(seed: Long): PersistedParkingRecord {
        val stored = SeededError(seed).displace(trueCar, parkedAccuracy)
        return PersistedParkingRecord(
            state = LifecycleState.PARKED,
            parkedLocation = ParkedLocation(
                stored.latitude,
                stored.longitude,
                parkedAccuracy,
                FakeWallClock.DEFAULT_START_EPOCH_MILLIS - PARKED_RECOVERY_WINDOW_MILLIS * 10,
            ),
        )
    }

    /** Walks from 300 m south of the car to it, one fix and one heading per guidance interval. */
    private fun walkBack(seed: Long): ReplayScript {
        val steps = mutableListOf<ReplayStep>(ReplayStep.Visibility(true))
        var south = 300.0
        var index = 0
        while (south > 0) {
            steps += ReplayStep.Fix(position(-south, (index % 7 - 3).toDouble()), fixAccuracy)
            steps += ReplayStep.Heading(((index * 13) % 360).toDouble())
            steps += ReplayStep.Advance(SAMPLING_INTERVAL_GUIDANCE_MILLIS)
            south -= 1.4
            index++
        }
        return ReplayScript(steps = steps, initialRecord = parkedRecord(seed + 1), trueCar = trueCar, errorSeed = seed)
    }

    /** @requirement FR-044 */
    @Test
    fun withAFixAndHeadingPresent_theFirstGuidanceIsPublishedOnTheVisibilityStep() = runTest {
        val script = ReplayScript(
            steps = listOf(
                ReplayStep.Fix(position(-80.0, 0.0), fixAccuracy),
                ReplayStep.Heading(6.0),
                ReplayStep.Visibility(true),
            ),
            initialRecord = parkedRecord(seed = 7),
            trueCar = trueCar,
        )
        val result = ReplayRunner.run(this, script)
        val visibilityTime = result.stepTimes[2]
        val firstGuidance = result.guidanceFrames().first { it.atMillis >= visibilityTime }
        val elapsed = firstGuidance.atMillis - visibilityTime
        assertEquals(0L, elapsed)
        assertTrue(elapsed <= TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS)
    }

    /** @requirement FR-045 */
    @Test
    fun overAWalkBackTheTrueCarIsInsideTheDrawnConeInAtLeastTheTargetShareOfFrames() = runTest {
        val result = ReplayRunner.run(this, walkBack(seed = 2026))
        assertTrue(result.guidanceFrames().size > 100, "frames: ${result.guidanceFrames().size}")
        val share = result.coneContainment()
        assertTrue(share >= CONE_CONTAINMENT_TARGET, "containment $share")
    }

    /** @requirement QR-016 */
    @Test
    fun theSameSeedGivesTheSameResult() = runTest {
        val first = ReplayRunner.run(this, walkBack(seed = 99))
        val second = ReplayRunner.run(this, walkBack(seed = 99))
        assertEquals(first.frames.map { it.state }, second.frames.map { it.state })
        assertEquals(first.coneContainment(), second.coneContainment())
    }

    /** @requirement FR-046 */
    @Test
    fun outsideTheRecoveryWindowEachFixOrHeadingStepChangesThePublishedStateBeforeTheNextStep() = runTest {
        val script = walkBack(seed = 5)
        val result = ReplayRunner.run(this, script)
        val publishedAt = result.frames.map { it.stepIndex }.toSet()
        // Steps taken while the cone was shown; inside the arrival zone a step may leave the view unchanged.
        val updateSteps = script.steps.indices.filter { index ->
            val isUpdate = script.steps[index] is ReplayStep.Fix || script.steps[index] is ReplayStep.Heading
            val before = result.frames.lastOrNull { it.stepIndex < index }?.state
            isUpdate && before is HomeScreenState.Guidance
        }
        assertTrue(updateSteps.size > 100, "steps checked: ${updateSteps.size}")
        val missing = updateSteps.filter { it !in publishedAt }
        assertTrue(missing.isEmpty(), "steps with no new state: $missing")
    }
}
