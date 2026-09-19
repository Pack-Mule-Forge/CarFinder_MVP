package com.packmuleforge.carfinder.shared.platform

import com.packmuleforge.carfinder.shared.annotation.Requirement

@Requirement("FR-034")
actual class Clock {
    actual fun nowEpochMillis(): Long = System.currentTimeMillis()
}
