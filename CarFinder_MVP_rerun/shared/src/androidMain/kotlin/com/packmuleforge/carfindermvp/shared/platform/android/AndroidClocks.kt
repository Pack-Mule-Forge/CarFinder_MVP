package com.packmuleforge.carfindermvp.shared.platform.android

import android.os.SystemClock
import com.packmuleforge.carfindermvp.shared.platform.MonotonicClock
import com.packmuleforge.carfindermvp.shared.platform.WallClock

class AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

class AndroidWallClock : WallClock {
    override fun epochMillis(): Long = System.currentTimeMillis()
}
