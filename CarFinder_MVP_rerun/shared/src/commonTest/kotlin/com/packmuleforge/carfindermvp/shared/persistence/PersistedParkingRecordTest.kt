package com.packmuleforge.carfindermvp.shared.persistence

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** @requirement QR-001 */
class PersistedParkingRecordTest {

    private val location = ParkedLocation(
        latitude = Readings.BASE_LATITUDE,
        longitude = Readings.BASE_LONGITUDE,
        accuracyMeters = Readings.GOOD_ACCURACY_METERS,
        declaredAtEpochMillis = 1_790_000_123_456L,
    )

    /** @requirement FR-020 */
    @Test
    fun givenParkedWithoutALocation_thenNormalizedIsFinding() {
        val record = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = null)
        assertEquals(PersistedParkingRecord.DEFAULT, record.normalized())
    }

    /** @requirement FR-017, FR-020 */
    @Test
    fun givenALocationWithAnyOtherState_thenNormalizedDropsTheLocation() {
        for (state in LifecycleState.entries - LifecycleState.PARKED) {
            val record = PersistedParkingRecord(state = state, parkedLocation = location)
            assertEquals(PersistedParkingRecord(state = state, parkedLocation = null), record.normalized())
        }
    }

    /** @requirement FR-017 */
    @Test
    fun givenAConsistentRecord_thenNormalizedEqualsItself() {
        val parked = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)
        assertEquals(parked, parked.normalized())
        for (state in LifecycleState.entries - LifecycleState.PARKED) {
            val record = PersistedParkingRecord(state = state, parkedLocation = null)
            assertEquals(record, record.normalized())
        }
    }

    /** @requirement FR-018 */
    @Test
    fun jsonRoundTripsIncludingTheDeclarationTime() {
        val record = PersistedParkingRecord(state = LifecycleState.PARKED, parkedLocation = location)
        val decoded = Json.decodeFromString<PersistedParkingRecord>(Json.encodeToString(record))
        assertEquals(record, decoded)
        assertEquals(location.declaredAtEpochMillis, decoded.parkedLocation?.declaredAtEpochMillis)
    }
}
