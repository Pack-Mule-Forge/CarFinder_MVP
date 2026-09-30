package com.packmuleforge.carfindermvp.shared.platform.android

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.packmuleforge.carfindermvp.shared.persistence.ParkingStore
import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

/**
 * Typed DataStore holding the single parking record. Writes replace the whole record atomically, and reads never
 * throw (research R4).
 *
 * @requirement FR-014, FR-018
 */
class DataStoreParkingStore private constructor(
    private val dataStore: DataStore<PersistedParkingRecord>,
) : ParkingStore {

    override suspend fun read(): PersistedParkingRecord = try {
        dataStore.data.first().normalized()
    } catch (e: IOException) {
        Log.w(TAG, "Parking record unreadable; using default", e)
        PersistedParkingRecord.DEFAULT
    }

    override suspend fun write(record: PersistedParkingRecord) {
        dataStore.updateData { record }
    }

    companion object {
        private const val TAG = "DataStoreParkingStore"
        private const val FILE_NAME = "parking_state.json"

        @Volatile
        private var instance: DataStoreParkingStore? = null

        /** Process singleton: DataStore forbids two active instances for one file. */
        fun getInstance(context: Context): DataStoreParkingStore = instance ?: synchronized(this) {
            instance ?: create(
                File(context.applicationContext.filesDir, "datastore/$FILE_NAME"),
                CoroutineScope(Dispatchers.IO + SupervisorJob()),
            ).also { instance = it }
        }

        /** Creates a store on [file]. Only one store may be active per file. */
        fun create(file: File, scope: CoroutineScope): DataStoreParkingStore = DataStoreParkingStore(
            DataStoreFactory.create(
                serializer = ParkingRecordSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { PersistedParkingRecord.DEFAULT },
                scope = scope,
                produceFile = { file },
            ),
        )
    }
}
