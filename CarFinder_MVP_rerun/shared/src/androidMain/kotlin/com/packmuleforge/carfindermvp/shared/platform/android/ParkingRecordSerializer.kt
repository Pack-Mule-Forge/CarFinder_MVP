package com.packmuleforge.carfindermvp.shared.platform.android

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * JSON for the one parking record. Undecodable data and an unknown schema version are reported as corruption so
 * the store's corruption handler replaces them.
 *
 * @requirement FR-018, FR-020
 */
internal object ParkingRecordSerializer : OkioSerializer<PersistedParkingRecord> {
    private val json = Json { encodeDefaults = true }

    override val defaultValue: PersistedParkingRecord = PersistedParkingRecord.DEFAULT

    override suspend fun readFrom(source: BufferedSource): PersistedParkingRecord {
        val record = try {
            json.decodeFromString<PersistedParkingRecord>(source.readUtf8())
        } catch (e: IllegalArgumentException) {
            // SerializationException and a failed ParkedLocation check are both IllegalArgumentExceptions.
            throw CorruptionException("Unreadable parking record", e)
        }
        if (record.schemaVersion != PersistedParkingRecord.CURRENT_SCHEMA_VERSION) {
            throw CorruptionException("Unknown parking record schema version")
        }
        return record
    }

    override suspend fun writeTo(t: PersistedParkingRecord, sink: BufferedSink) {
        sink.writeUtf8(json.encodeToString(t))
    }
}
