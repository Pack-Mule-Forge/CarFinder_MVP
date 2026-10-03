package com.packmuleforge.carfindermvp.shared.platform

/** An opaque platform handle (the Android `Context`). No test substitutes it. */
expect abstract class PlatformContext

/**
 * The one platform boundary: builds every adapter for the running platform (constitution Principle V).
 *
 * @requirement QR-014
 */
expect fun createPlatformAdapters(context: PlatformContext): PlatformAdapters
