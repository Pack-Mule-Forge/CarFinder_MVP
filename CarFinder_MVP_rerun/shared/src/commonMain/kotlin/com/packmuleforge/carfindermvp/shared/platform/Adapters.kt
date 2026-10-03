package com.packmuleforge.carfindermvp.shared.platform

import com.packmuleforge.carfindermvp.shared.domain.LocationReading
import com.packmuleforge.carfindermvp.shared.guidance.HeadingReading
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single location stream shared by detection and guidance.
 *
 * @requirement FR-029
 */
interface LocationSource {
    val readings: SharedFlow<LocationReading>

    /** Re-issues the single platform request at this interval (FR-027). */
    fun setIntervalMillis(intervalMillis: Long)
    fun start()
    fun stop()
}

/**
 * Smoothed, display-remapped, true-north heading; `null` while unreliable or without a sensor.
 *
 * @requirement FR-033, FR-034, FR-041
 */
interface HeadingSource {
    val heading: StateFlow<HeadingReading?>
    fun start()
    fun stop()
}

/**
 * The platform's in-vehicle hint, used only to raise the sampling rate.
 *
 * @requirement FR-028
 */
interface ActivitySignalSource {
    val isInVehicle: StateFlow<Boolean>
    fun start()
    fun stop()
}

/**
 * The single persisted parking record.
 *
 * @requirement FR-018, FR-020
 */
interface ParkingStore {
    /** Never throws: unreadable data yields [PersistedParkingRecord.DEFAULT] and is logged. */
    suspend fun read(): PersistedParkingRecord
    suspend fun write(record: PersistedParkingRecord)
}

/**
 * Each platform's runtime permission flow, normalized into one suspend function (constitution Principle V).
 *
 * @requirement FR-047, FR-048, FR-049, FR-056
 */
interface PermissionController {
    val status: StateFlow<PermissionState>

    /** Shows exactly one platform prompt for [capability]; returns whether it is now granted. */
    suspend fun request(capability: Capability): Boolean

    /** Re-reads grants from the platform. */
    fun refresh()
}

interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

interface WallClock {
    fun epochMillis(): Long
}

/**
 * Diagnostic events; never carries coordinates.
 *
 * @requirement FR-020
 */
interface DiagnosticLog {
    fun record(event: DiagnosticEvent)
}

/** @requirement FR-020 */
sealed interface DiagnosticEvent {
    data object StoreUnreadable : DiagnosticEvent
    data object RecordNormalized : DiagnosticEvent
    data object ReadingDropped : DiagnosticEvent
}

/**
 * The runtime-gated capabilities, in request order.
 *
 * @requirement FR-047, FR-048
 */
enum class Capability { FINE_LOCATION, BACKGROUND_LOCATION, ACTIVITY_RECOGNITION, NOTIFICATIONS }

/** @requirement FR-048 */
enum class PermissionStatus { NOT_REQUESTED, GRANTED, DENIED }

/**
 * The status of every capability. A capability missing from [statuses] is `NOT_REQUESTED`.
 *
 * @requirement FR-049
 */
data class PermissionState(val statuses: Map<Capability, PermissionStatus> = emptyMap()) {
    operator fun get(capability: Capability): PermissionStatus = statuses[capability] ?: PermissionStatus.NOT_REQUESTED

    fun isGranted(capability: Capability): Boolean = get(capability) == PermissionStatus.GRANTED

    /** Location and notifications are the required permissions (FR-049). */
    val areRequiredGranted: Boolean
        get() = REQUIRED.all { isGranted(it) }

    fun with(capability: Capability, status: PermissionStatus): PermissionState =
        copy(statuses = statuses + (capability to status))

    companion object {
        val REQUIRED: Set<Capability> = setOf(Capability.FINE_LOCATION, Capability.NOTIFICATIONS)
        val ALL_GRANTED = PermissionState(Capability.entries.associateWith { PermissionStatus.GRANTED })
    }
}

/**
 * A required capability whose denial awaits the user's answer, or whose closing the user confirmed. Transient.
 *
 * @requirement FR-056
 */
data class DenialConfirmation(val capability: Capability, val isClosing: Boolean)

/** Every platform service the shared core uses, built by the platform factory or from fakes in tests. */
class PlatformAdapters(
    val location: LocationSource,
    val heading: HeadingSource,
    val activity: ActivitySignalSource,
    val store: ParkingStore,
    val permissions: PermissionController,
    val monotonicClock: MonotonicClock,
    val wallClock: WallClock,
    val log: DiagnosticLog,
)
