package com.packmuleforge.carfinder.shared.platform

import com.packmuleforge.carfinder.shared.annotation.Requirement

@Requirement("FR-034")
class AndroidClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
