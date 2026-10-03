package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import com.packmuleforge.carfindermvp.shared.platform.WallClock
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A monotonic clock that follows [scheduler]'s virtual time, if given, plus any [advanceBy]. */
class FakeMonotonicClock(
    private val scheduler: TestCoroutineScheduler? = null,
    startMillis: Long = 0,
) : MonotonicClock {
    private var offset = startMillis

    override fun elapsedRealtimeMillis(): Long = offset + (scheduler?.currentTime ?: 0)

    fun advanceBy(millis: Long) {
        offset += millis
    }
}

/** A wall clock that follows [scheduler]'s virtual time, if given, plus any [advanceBy]. */
class FakeWallClock(
    private val scheduler: TestCoroutineScheduler? = null,
    startEpochMillis: Long = DEFAULT_START_EPOCH_MILLIS,
) : WallClock {
    private var offset = startEpochMillis

    override fun epochMillis(): Long = offset + (scheduler?.currentTime ?: 0)

    fun advanceBy(millis: Long) {
        offset += millis
    }

    companion object {
        const val DEFAULT_START_EPOCH_MILLIS = 1_790_000_000_000L
    }
}
