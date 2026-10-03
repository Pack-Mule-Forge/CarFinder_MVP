package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticLog
import com.packmuleforge.carfindermvp.shared.platform.ParkingStore

/**
 * A store held in memory. [makeUnreadable] behaves like a corrupt file: the next read yields
 * [PersistedParkingRecord.DEFAULT], reports [DiagnosticEvent.StoreUnreadable] and resets the record.
 */
class InMemoryParkingStore(private val log: DiagnosticLog? = null) : ParkingStore {
    var record: PersistedParkingRecord = PersistedParkingRecord.DEFAULT
        private set

    /** Every record written, in order. */
    val writes = mutableListOf<PersistedParkingRecord>()
    var readCount = 0
        private set
    private var isUnreadable = false

    /** Sets the stored record without counting a write. */
    fun seed(record: PersistedParkingRecord) {
        this.record = record
        isUnreadable = false
    }

    fun makeUnreadable() {
        isUnreadable = true
    }

    override suspend fun read(): PersistedParkingRecord {
        readCount++
        if (isUnreadable) {
            isUnreadable = false
            record = PersistedParkingRecord.DEFAULT
            log?.record(DiagnosticEvent.StoreUnreadable)
        }
        return record
    }

    override suspend fun write(record: PersistedParkingRecord) {
        this.record = record
        isUnreadable = false
        writes += record
    }
}
