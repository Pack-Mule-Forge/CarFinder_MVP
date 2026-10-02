package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import com.packmuleforge.carfindermvp.shared.platform.WallClock

class FakeMonotonicClock(var now: Long = 0L) : MonotonicClock {
    fun advanceBy(millis: Long) {
        now += millis
    }

    override fun elapsedRealtimeMillis(): Long = now
}

class FakeWallClock(var now: Long = 0L) : WallClock {
    override fun epochMillis(): Long = now
}
