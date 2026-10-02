package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.domain.SamplingProfile
import kotlinx.coroutines.flow.Flow

/**
 * Platform location provider. Implementations MUST:
 * - keep exactly one platform subscription, re-issued on [setProfile];
 * - map a missing or invalid accuracy or speed to null, never to a default value;
 * - stamp every reading with the monotonic fix time;
 * - never throw into the collector: a permission failure completes the flow and is logged.
 *
 * @requirement QR-010
 */
interface LocationSource {
    /** Hot stream while started; shared by the engine and guidance. */
    val readings: Flow<LocationReading>

    fun setProfile(profile: SamplingProfile)

    fun start()

    fun stop()
}
