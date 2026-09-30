package com.packmuleforge.carfinder_mvp.repository

import androidx.datastore.core.DataStoreFactory
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder_mvp.adapter.DataStoreParkedLocationRepository
import com.packmuleforge.carfinder_mvp.adapter.ParkingDataSerializer
import com.packmuleforge.carfinder_mvp.data.ParkingDataProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.io.OutputStream

/**
 * Pure JVM test for DataStoreParkedLocationRepository (T031). Uses a real Proto DataStore backed
 * by a JUnit TemporaryFolder file — no Robolectric, no MockK, no Android Context.
 */
@Requirement("FR-011")
class DataStoreParkedLocationRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // DataStore refuses a second instance over the same file while an earlier instance's scope
    // is still active (androidx.datastore.core.SingleProcessDataStore.activeFiles). Each test
    // simulates an app restart by pointing a fresh repository at the same file, so each instance
    // needs its own cancellable scope, cancelled (and joined, so the internal activeFiles entry
    // is actually released) before the next instance is created.
    private fun repositoryOver(file: java.io.File, scope: CoroutineScope) = DataStoreParkedLocationRepository(
        DataStoreFactory.create(
            serializer = ParkingDataSerializer,
            scope = scope,
            produceFile = { file }
        )
    )

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun CoroutineScope.disposeDataStore() {
        coroutineContext[Job]!!.cancelAndJoin()
    }

    // repository.load() collects dataStore.data, a flow that never completes while the
    // DataStore's own scope is alive (see CR-9 in analysis-findings.md) — bounding it here so an
    // unexpected regression fails the test instead of hanging the whole Gradle run forever.
    private suspend fun DataStoreParkedLocationRepository.loadBounded() =
        withTimeout(5_000) { load() }

    @Test
    fun writtenRecordSurvivesRepositoryReinstantiation() = runBlocking {
        // Do not pre-create the file: DataStore's write path renames a scratch file onto it,
        // and File.renameTo() fails on Windows when the destination already exists (unlike the
        // POSIX atomic-replace semantics androidx.datastore.core.SingleProcessDataStore assumes).
        val file = java.io.File(tempFolder.newFolder(), "parking_data.pb")
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = 1_000L
        )

        val firstScope = newScope()
        val firstRepository = repositoryOver(file, firstScope)
        firstRepository.save(ParkingState.PARKED, location)
        firstScope.disposeDataStore()

        // A fresh repository instance, backed by a new DataStore over the same file, must see the write.
        val secondScope = newScope()
        val secondRepository = repositoryOver(file, secondScope)
        val reloaded = secondRepository.loadBounded()
        secondScope.disposeDataStore()

        assertEquals(ParkingState.PARKED, reloaded.state)
        assertNotNull(reloaded.parkedLocation)
        assertEquals(location.point.latitudeDegrees, reloaded.parkedLocation?.point?.latitudeDegrees)
        assertEquals(location.capturedAtEpochMillis, reloaded.parkedLocation?.capturedAtEpochMillis)
    }

    @Test
    fun interruptedWriteLeavesPriorRecordIntact() = runBlocking {
        // Do not pre-create the file: DataStore's write path renames a scratch file onto it,
        // and File.renameTo() fails on Windows when the destination already exists (unlike the
        // POSIX atomic-replace semantics androidx.datastore.core.SingleProcessDataStore assumes).
        val file = java.io.File(tempFolder.newFolder(), "parking_data.pb")
        val location = ParkedLocation(
            point = GeoPoint(37.7749, -122.4194, 15.0),
            capturedAtEpochMillis = 2_000L
        )

        val goodScope = newScope()
        val goodRepository = repositoryOver(file, goodScope)
        goodRepository.save(ParkingState.PARKED, location)
        goodScope.disposeDataStore()

        // A DataStore whose writeTo throws simulates an interrupted write. The write must not
        // reach disk in a torn state/location pair — the prior record must remain readable.
        val throwingScope = newScope()
        val throwingDataStore = DataStoreFactory.create(
            serializer = ThrowingWriteSerializer,
            scope = throwingScope,
            produceFile = { file }
        )
        val throwingRepository = DataStoreParkedLocationRepository(throwingDataStore)

        try {
            throwingRepository.save(ParkingState.DRIVING, null)
        } catch (e: Exception) {
            // Expected: the simulated write failure propagates rather than being silently swallowed.
        }
        throwingScope.disposeDataStore()

        val recoveredScope = newScope()
        val recoveredRepository = repositoryOver(file, recoveredScope)
        val afterFailedWrite = recoveredRepository.loadBounded()
        recoveredScope.disposeDataStore()

        assertEquals(ParkingState.PARKED, afterFailedWrite.state)
        assertNotNull(afterFailedWrite.parkedLocation)
        assertEquals(location.capturedAtEpochMillis, afterFailedWrite.parkedLocation?.capturedAtEpochMillis)
    }
}

/** Delegates reads to the real serializer but always fails on write, simulating an interrupted write. */
private object ThrowingWriteSerializer : androidx.datastore.core.Serializer<ParkingDataProto.ParkingData> {
    override val defaultValue: ParkingDataProto.ParkingData = ParkingDataSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): ParkingDataProto.ParkingData =
        ParkingDataSerializer.readFrom(input)

    override suspend fun writeTo(t: ParkingDataProto.ParkingData, output: OutputStream) {
        throw java.io.IOException("Simulated interrupted write")
    }
}
