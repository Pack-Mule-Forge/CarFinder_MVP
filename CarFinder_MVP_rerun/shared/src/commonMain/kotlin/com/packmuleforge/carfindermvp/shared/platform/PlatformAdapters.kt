package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.persistence.ParkingStore

/**
 * Every platform service the shared core needs, bundled so the engine and presenter depend only on interfaces.
 *
 * @requirement QR-010
 */
class PlatformAdapters(
    val location: LocationSource,
    val heading: HeadingSource,
    val activity: ActivitySignalSource,
    val store: ParkingStore,
    val monotonicClock: MonotonicClock,
    val wallClock: WallClock,
    val permissions: PermissionController,
)

/** The platform's application context: `android.content.Context` on Android. */
expect abstract class PlatformContext

/**
 * The expect/actual boundary: each platform wires its native adapters behind the common interfaces
 * (Constitution V).
 *
 * @requirement QR-010
 */
expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters
