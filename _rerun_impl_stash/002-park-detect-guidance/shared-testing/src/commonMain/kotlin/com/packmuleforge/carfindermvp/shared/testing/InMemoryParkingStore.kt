package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.persistence.ParkingStore
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord

/** Honors the [ParkingStore] contract: reads never throw, and a simulated corruption reads as the default. */
class InMemoryParkingStore(initial: PersistedParkingRecord = PersistedParkingRecord.DEFAULT) : ParkingStore {
    var record: PersistedParkingRecord = initial
        private set

    /** Every record passed to [write], in order. */
    val writes = mutableListOf<PersistedParkingRecord>()

    /** When true, the next [read] behaves like a corrupted file and returns the default record. */
    var failNextRead = false

    fun seed(record: PersistedParkingRecord) {
        this.record = record
    }

    override suspend fun read(): PersistedParkingRecord {
        if (failNextRead) {
            failNextRead = false
            return PersistedParkingRecord.DEFAULT
        }
        return record
    }

    override suspend fun write(record: PersistedParkingRecord) {
        this.record = record
        writes += record
    }
}
