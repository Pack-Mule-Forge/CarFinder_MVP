package com.packmuleforge.carfinder.shared.platform

/**
 * Platform abstraction for getting the current time. Implemented separately on Android and iOS.
 */
interface Clock {
    fun nowEpochMillis(): Long
}
