package com.packmuleforge.carfindermvp.shared.platform

import kotlinx.coroutines.flow.Flow

/**
 * Activity-recognition hint. It only raises the sampling rate; it is never a lifecycle input (research R2).
 * Implementations MUST emit only on IN_VEHICLE enter, and on any failure (including a denied permission) emit
 * nothing.
 *
 * @requirement QR-010
 */
interface ActivitySignalSource {
    val inVehicleEntered: Flow<Unit>

    fun start()

    fun stop()
}
