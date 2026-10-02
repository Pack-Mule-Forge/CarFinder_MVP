package com.packmuleforge.carfindermvp.shared.persistence

/**
 * Storage for the single [PersistedParkingRecord]. The mechanism is a platform concern.
 *
 * @requirement FR-014, QR-010
 */
interface ParkingStore {
    /** Never throws: a missing, corrupted or unknown-schema record reads as [PersistedParkingRecord.DEFAULT]. */
    suspend fun read(): PersistedParkingRecord

    /** Replaces the whole record atomically. */
    suspend fun write(record: PersistedParkingRecord)
}
