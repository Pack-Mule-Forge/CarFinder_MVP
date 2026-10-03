package com.packmuleforge.carfindermvp.shared.platform.android

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import com.packmuleforge.carfindermvp.shared.platform.DiagnosticLog
import com.packmuleforge.carfindermvp.shared.platform.ParkingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

/**
 * The parking record in one typed DataStore file, replaced atomically on every write. Corrupt or
 * unknown-version data is replaced with the default record and logged.
 *
 * Okio storage is used because its atomic move replaces the existing file on every host OS; the plain file
 * storage's rename cannot overwrite a file on Windows, where the host tests run.
 *
 * @requirement FR-018, FR-020
 */
class DataStoreParkingStore(
    produceFile: () -> File,
    scope: CoroutineScope,
    private val log: DiagnosticLog,
) : ParkingStore {

    private val dataStore = DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = ParkingRecordSerializer,
            producePath = { produceFile().absoluteFile.toOkioPath() },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler {
            log.record(DiagnosticEvent.StoreUnreadable)
            PersistedParkingRecord.DEFAULT
        },
        scope = scope,
    )

    override suspend fun read(): PersistedParkingRecord = try {
        dataStore.data.first()
    } catch (_: IOException) {
        log.record(DiagnosticEvent.StoreUnreadable)
        PersistedParkingRecord.DEFAULT
    }

    override suspend fun write(record: PersistedParkingRecord) {
        try {
            dataStore.updateData { record }
        } catch (_: IOException) {
            // The record could not be stored; the next write tries again. There is no separate write-failure event.
            log.record(DiagnosticEvent.StoreUnreadable)
        }
    }
}
