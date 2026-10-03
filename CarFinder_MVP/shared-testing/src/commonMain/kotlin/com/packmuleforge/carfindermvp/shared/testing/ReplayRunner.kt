package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.engine.HomeScreenPresenter
import com.packmuleforge.carfindermvp.shared.engine.HomeScreenState
import com.packmuleforge.carfindermvp.shared.engine.ParkingEngine
import com.packmuleforge.carfindermvp.shared.guidance.GeoMath
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.math.abs

/** One published home-screen state, with the time, the step that caused it and the truth at that moment. */
data class ReplayFrame(
    val atMillis: Long,
    val stepIndex: Int,
    val state: HomeScreenState,
    val trueUser: TruePosition?,
    val trueHeadingDegrees: Double?,
)

/** @requirement FR-045, QR-016 */
class ReplayResult(
    val frames: List<ReplayFrame>,
    /** The virtual time at which each step started. */
    val stepTimes: List<Long>,
    private val trueCar: TruePosition?,
) {
    /** Frames in which a cone is drawn; arrival and unavailable frames are not guidance frames (FR-045). */
    fun guidanceFrames(): List<ReplayFrame> = frames.filter { it.state is HomeScreenState.Guidance }

    /** The share of guidance frames in which the true car lies inside the drawn cone (FR-045). */
    fun coneContainment(): Double {
        val car = checkNotNull(trueCar) { "the script has no true car" }
        val counted = guidanceFrames().filter { it.trueUser != null && it.trueHeadingDegrees != null }
        if (counted.isEmpty()) return 0.0
        return counted.count { isInside(it, car) }.toDouble() / counted.size
    }

    /**
     * Inside when the true bearing from the true user to the true car, taken relative to the true heading,
     * differs from the drawn display bearing by no more than the drawn half-angle (FR-045).
     */
    private fun isInside(frame: ReplayFrame, car: TruePosition): Boolean {
        val cone = (frame.state as HomeScreenState.Guidance).cone
        val user = checkNotNull(frame.trueUser)
        val trueBearing = GeoMath.initialBearingDegrees(user.latitude, user.longitude, car.latitude, car.longitude)
        val relative = GeoMath.normalizeDegrees(trueBearing - checkNotNull(frame.trueHeadingDegrees))
        val difference = abs(relative - cone.displayBearingDegrees).let { minOf(it, FULL_TURN - it) }
        return difference <= cone.halfAngleDegrees
    }

    private companion object {
        const val FULL_TURN = 360.0
    }
}

/**
 * Plays a [ReplayScript] through the real [ParkingEngine] and [HomeScreenPresenter], with fakes on the test's
 * virtual clock, exactly as a live stream would arrive, and records every published [HomeScreenState] (QR-016).
 *
 * @requirement QR-016
 */
object ReplayRunner {
    suspend fun run(test: TestScope, script: ReplayScript): ReplayResult = with(test) {
        val platform = FakePlatform(testScheduler)
        platform.store.seed(script.initialRecord)
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val engine = ParkingEngine(platform.adapters, scope)
        val presenter = HomeScreenPresenter(engine, platform.adapters, scope)
        val error = script.errorSeed?.let(::SeededError)

        val frames = mutableListOf<ReplayFrame>()
        val stepTimes = mutableListOf<Long>()
        var stepIndex = -1
        var trueUser: TruePosition? = null
        var trueHeading: Double? = null
        scope.launch(UnconfinedTestDispatcher(testScheduler)) {
            presenter.state.collect { state ->
                frames += ReplayFrame(testScheduler.currentTime, stepIndex, state, trueUser, trueHeading)
            }
        }
        engine.start()
        runCurrent()

        for ((index, step) in script.steps.withIndex()) {
            stepIndex = index
            stepTimes += testScheduler.currentTime
            val now = platform.monotonicClock.elapsedRealtimeMillis()
            when (step) {
                is ReplayStep.Fix -> {
                    trueUser = step.truePosition
                    val accuracy = step.accuracyMeters
                    val reported = if (error != null && accuracy != null) error.displace(step.truePosition, accuracy) else step.truePosition
                    platform.location.emit(
                        Readings.readingAt(reported.latitude, reported.longitude, accuracy, step.speedMph, now),
                    )
                }
                is ReplayStep.Heading -> {
                    trueHeading = step.trueHeadingDegrees
                    platform.heading.emit(step.trueHeadingDegrees?.let { HeadingReading(GeoMath.normalizeDegrees(it), now) })
                }
                is ReplayStep.Visibility -> presenter.onGuidanceVisible(step.visible)
                is ReplayStep.InVehicle -> platform.activity.set(step.inVehicle)
                is ReplayStep.Permission -> platform.permissions.setStatus(step.capability, step.status)
                is ReplayStep.Advance -> advanceTimeBy(step.millis)
            }
            runCurrent()
        }
        scope.cancel()
        ReplayResult(frames.toList(), stepTimes.toList(), script.trueCar)
    }
}
