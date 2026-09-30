package com.packmuleforge.carfindermvp.shared.platform.android

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON serializer for the parking record. Any parse failure or unknown schema version is a corruption, which
 * DataStore's corruption handler replaces with the default FINDING record (FR-018).
 *
 * @requirement FR-014, FR-018
 */
internal object ParkingRecordSerializer : Serializer<PersistedParkingRecord> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: PersistedParkingRecord = PersistedParkingRecord.DEFAULT

    override suspend fun readFrom(input: InputStream): PersistedParkingRecord {
        val record = try {
            json.decodeFromString(PersistedParkingRecord.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable parking record", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Invalid parking record", e)
        }
        if (record.schemaVersion != PersistedParkingRecord.CURRENT_SCHEMA_VERSION) {
            throw CorruptionException("Unknown parking record schema version ${record.schemaVersion}")
        }
        return record
    }

    override suspend fun writeTo(t: PersistedParkingRecord, output: OutputStream) {
        output.write(json.encodeToString(PersistedParkingRecord.serializer(), t).encodeToByteArray())
    }
}
