package com.packmuleforge.carfindermvp.shared.platform.android

import com.packmuleforge.carfindermvp.shared.domain.LifecycleState
import com.packmuleforge.carfindermvp.shared.domain.ParkedLocation
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.testing.Readings
import com.packmuleforge.carfindermvp.shared.testing.RecordingDiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-003 */
class DataStoreParkingStoreTest {

    private val dir = createTempDirectory("parking-store").toFile()
    private val file = File(dir, "parking_record.json")
    private val log = RecordingDiagnosticLog()
    private val scopes = mutableListOf<CoroutineScope>()

    private val parked = PersistedParkingRecord(
        state = LifecycleState.PARKED,
        parkedLocation = ParkedLocation(
            Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS, 1_790_000_123_456L,
        ),
    )

    private fun newStore(): DataStoreParkingStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return DataStoreParkingStore(produceFile = { file }, scope = scope, log = log)
    }

    private suspend fun closeStores() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @AfterTest
    fun cleanUp() {
        runBlocking { closeStores() }
        dir.deleteRecursively()
    }

    /** @requirement FR-018 */
    @Test
    fun writeThenReadRoundTripsStateAndLocation() = runBlocking {
        val store = newStore()
        store.write(parked)
        assertEquals(parked, store.read())
    }

    /** @requirement FR-018 */
    @Test
    fun aSecondWriteReplacesTheFirst() = runBlocking {
        val store = newStore()
        store.write(parked)
        store.write(PersistedParkingRecord.DEFAULT)
        closeStores()
        assertEquals(PersistedParkingRecord.DEFAULT, newStore().read())
        assertTrue(log.events.isEmpty(), "log: ${log.events}")
    }

    /** @requirement FR-018 */
    @Test
    fun aNewStoreOnTheSameFileReadsTheSameRecord() = runBlocking {
        newStore().write(parked)
        closeStores()
        assertEquals(parked, newStore().read())
    }

    /** @requirement FR-020 */
    @Test
    fun givenGarbageBytes_thenDefaultIsReadAndLoggedAndTheNextWriteSucceeds() = runBlocking {
        file.writeBytes(byteArrayOf(0x7b, 0x00, 0x13, 0x37, 0x2c))
        val store = newStore()
        assertEquals(PersistedParkingRecord.DEFAULT, store.read())
        assertTrue(DiagnosticEvent.StoreUnreadable in log.events)
        store.write(parked)
        assertEquals(parked, store.read())
        closeStores()
        assertEquals(parked, newStore().read())
    }

    /** @requirement FR-020 */
    @Test
    fun givenAnUnknownSchemaVersion_thenItIsTreatedAsUnreadable() = runBlocking {
        file.writeText("""{"schemaVersion":99,"state":"PARKED","parkedLocation":null}""")
        val store = newStore()
        assertEquals(PersistedParkingRecord.DEFAULT, store.read())
        assertTrue(DiagnosticEvent.StoreUnreadable in log.events)
    }

    /** @requirement FR-018 */
    @Test
    fun givenNoFile_thenDefaultIsReadWithoutLogging() = runBlocking {
        assertEquals(PersistedParkingRecord.DEFAULT, newStore().read())
        assertTrue(log.events.isEmpty())
    }
}
