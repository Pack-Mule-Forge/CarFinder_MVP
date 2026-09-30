package com.packmuleforge.carfindermvp.shared.persistence

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * @requirement QR-001
 */
class PersistedParkingRecordTest {

    private val location = ParkedLocation(
        latitude = 37.42,
        longitude = -122.08,
        accuracyMeters = 11.3,
        capturedAtEpochMillis = 1_790_000_000_000,
    )
    private val json = Json { encodeDefaults = true }

    /** @requirement FR-011 */
    @Test
    fun defaultRecord_isFindingWithNoLocation() {
        val default = PersistedParkingRecord.DEFAULT
        assertEquals(PersistedParkingRecord.CURRENT_SCHEMA_VERSION, default.schemaVersion)
        assertEquals(LifecycleState.FINDING, default.state)
        assertNull(default.parkedLocation)
    }

    /** @requirement FR-018 */
    @Test
    fun normalized_parkedWithoutLocation_becomesFinding() {
        val broken = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = null)
        assertEquals(PersistedParkingRecord.DEFAULT, broken.normalized())
    }

    /** @requirement FR-018 */
    @Test
    fun normalized_nonParkedWithLocation_dropsLocation() {
        for (state in LifecycleState.entries.filter { it != LifecycleState.PARKED }) {
            val record = PersistedParkingRecord(state = state, parkedLocation = location)
            val normalized = record.normalized()
            assertEquals(state, normalized.state)
            assertNull(normalized.parkedLocation)
        }
    }

    /** @requirement FR-018 */
    @Test
    fun normalized_validParkedRecord_isUnchanged() {
        val record = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)
        assertEquals(record, record.normalized())
    }

    /** @requirement FR-014 */
    @Test
    fun jsonRoundTrip_preservesEveryField() {
        val record = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)
        val decoded = json.decodeFromString(PersistedParkingRecord.serializer(), json.encodeToString(
            PersistedParkingRecord.serializer(), record))
        assertEquals(record, decoded)
    }

    /** @requirement FR-013 */
    @Test
    fun record_holdsExactlyOneParkedLocationObject_andNoCollection() {
        val record = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)
        val tree = json.parseToJsonElement(json.encodeToString(PersistedParkingRecord.serializer(), record))
            .jsonObject
        assertEquals(setOf("schemaVersion", "state", "parkedLocation"), tree.keys)
        assertIs<JsonObject>(tree["parkedLocation"])
    }
}
