package com.packmuleforge.carfindermvp.shared.platform.android

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Real DataStore on a temporary file: persistence and corruption fallback without a device.
 * @requirement QR-004
 */
class DataStoreParkingStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private fun storeFor(file: File): DataStoreParkingStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreParkingStore.create(file, scope)
    }

    private val parked = PersistedParkingRecord(
        state = LifecycleState.PARKED,
        parkedLocation = ParkedLocation(Readings.BASE_LAT, Readings.BASE_LON, Readings.GOOD_ACCURACY_METERS, 42L),
    )

    /** @requirement FR-014 */
    @Test
    fun writeThenRead_roundTrips_acrossStoreInstances() = runBlocking {
        val file = File(folder.root, "round-trip.json")
        storeFor(file).write(parked)
        // Simulate process death: shut the first store down completely before reopening the same file.
        scopes.removeLast().let { it.cancel(); it.coroutineContext[Job]!!.join() }
        assertEquals(parked, storeFor(file).read())
    }

    /** @requirement FR-011, FR-014 */
    @Test
    fun missingFile_readsAsDefault() = runBlocking {
        assertEquals(PersistedParkingRecord.DEFAULT, storeFor(File(folder.root, "missing.json")).read())
    }

    /** @requirement FR-018 */
    @Test
    fun garbageBytes_readAsDefault_withoutThrowing() = runBlocking {
        val file = File(folder.root, "garbage.json").apply { writeBytes(byteArrayOf(0x00, 0x7F, 0x13, 0x37)) }
        assertEquals(PersistedParkingRecord.DEFAULT, storeFor(file).read())
    }

    /** @requirement FR-018 */
    @Test
    fun unknownSchemaVersion_readsAsDefault() = runBlocking {
        val future = PersistedParkingRecord.CURRENT_SCHEMA_VERSION + 1
        val file = File(folder.root, "future.json").apply {
            writeText("""{"schemaVersion":$future,"state":"PARKED","parkedLocation":null}""")
        }
        assertEquals(PersistedParkingRecord.DEFAULT, storeFor(file).read())
    }

    /** @requirement FR-014, FR-018 */
    @Test
    fun write_replacesWholeRecord_soStateAndLocationAgree() = runBlocking {
        val store = storeFor(File(folder.root, "replace.json"))
        store.write(parked)
        store.write(PersistedParkingRecord(state = LifecycleState.DRIVING))
        val read = store.read()
        assertEquals(LifecycleState.DRIVING, read.state)
        assertNull(read.parkedLocation)
    }
}
