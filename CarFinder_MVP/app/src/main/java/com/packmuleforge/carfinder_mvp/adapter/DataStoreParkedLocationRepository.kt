package com.packmuleforge.carfinder_mvp.adapter

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import com.google.protobuf.kotlin.toByteString
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GeoPoint
import com.packmuleforge.carfinder.shared.model.ParkedLocation
import com.packmuleforge.carfinder.shared.model.ParkingState
import com.packmuleforge.carfinder.shared.repository.ParkedLocationRepository
import com.packmuleforge.carfinder.shared.repository.PersistedParkingData
import com.packmuleforge.carfinder_mvp.data.parkingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.packmuleforge.carfinder_mvp.data.ParkingDataProto
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * Android implementation of ParkedLocationRepository using Proto DataStore.
 * Provides atomic persistence of parking state and location (FR-011).
 */
@Requirement("FR-011", "FR-012", "FR-013", "FR-014")
class DataStoreParkedLocationRepository(
    context: Context
) : ParkedLocationRepository {
    private val dataStore: DataStore<ParkingDataProto.ParkingData> = context.createDataStore(
        fileName = "parking_data.pb",
        serializer = ParkingDataSerializer
    )

    override fun observe(): Flow<PersistedParkingData> {
        return dataStore.data.map { proto ->
            proto.toPersistedData()
        }
    }

    override suspend fun load(): PersistedParkingData {
        return dataStore.data.map { proto ->
            proto.toPersistedData()
        }.let { flow ->
            // Collect first emission
            var result = PersistedParkingData(ParkingState.FINDING, null)
            flow.collect { data ->
                result = data
            }
            result
        }
    }

    override suspend fun save(state: ParkingState, parkedLocation: ParkedLocation?) {
        dataStore.updateData { currentData ->
            currentData.toBuilder().apply {
                this.state = state.toProtoState()
                if (parkedLocation != null) {
                    this.parkedLocation = ParkingDataProto.ParkingData.ParkedLocationData.newBuilder().apply {
                        point = ParkingDataProto.ParkingData.GeoPoint.newBuilder().apply {
                            latitudeDegrees = parkedLocation.point.latitudeDegrees
                            longitudeDegrees = parkedLocation.point.longitudeDegrees
                            accuracyRadiusMeters = parkedLocation.point.accuracyRadiusMeters
                        }.build()
                        capturedAtEpochMillis = parkedLocation.capturedAtEpochMillis
                    }.build()
                } else {
                    clearParkedLocation()
                }
            }.build()
        }
    }

    override suspend fun clearLocation() {
        dataStore.updateData { currentData ->
            currentData.toBuilder().apply {
                clearParkedLocation()
            }.build()
        }
    }

    private fun ParkingDataProto.ParkingData.toPersistedData(): PersistedParkingData {
        val state = when (this.state) {
            ParkingDataProto.ParkingData.State.DRIVING -> ParkingState.DRIVING
            ParkingDataProto.ParkingData.State.PARKING -> ParkingState.PARKING
            ParkingDataProto.ParkingData.State.PARKED -> ParkingState.PARKED
            ParkingDataProto.ParkingData.State.FINDING -> ParkingState.FINDING
            else -> ParkingState.FINDING
        }

        val location = if (this.hasParkedLocation()) {
            val proto = this.parkedLocation
            ParkedLocation(
                point = GeoPoint(
                    latitudeDegrees = proto.point.latitudeDegrees,
                    longitudeDegrees = proto.point.longitudeDegrees,
                    accuracyRadiusMeters = proto.point.accuracyRadiusMeters
                ),
                capturedAtEpochMillis = proto.capturedAtEpochMillis
            )
        } else {
            null
        }

        return PersistedParkingData(state, location)
    }

    private fun ParkingState.toProtoState(): ParkingDataProto.ParkingData.State {
        return when (this) {
            ParkingState.DRIVING -> ParkingDataProto.ParkingData.State.DRIVING
            ParkingState.PARKING -> ParkingDataProto.ParkingData.State.PARKING
            ParkingState.PARKED -> ParkingDataProto.ParkingData.State.PARKED
            ParkingState.FINDING -> ParkingDataProto.ParkingData.State.FINDING
        }
    }

    companion object {
        private object ParkingDataSerializer : Serializer<ParkingDataProto.ParkingData> {
            override val defaultValue: ParkingDataProto.ParkingData = ParkingDataProto.ParkingData.newBuilder()
                .setState(ParkingDataProto.ParkingData.State.FINDING)
                .build()

            override suspend fun readFrom(input: InputStream): ParkingDataProto.ParkingData {
                return try {
                    ParkingDataProto.ParkingData.parseFrom(input)
                } catch (e: Exception) {
                    defaultValue
                }
            }

            override suspend fun writeTo(t: ParkingDataProto.ParkingData, output: OutputStream) {
                t.writeTo(output)
            }
        }
    }
}

private fun <T> Context.createDataStore(
    fileName: String,
    serializer: Serializer<T>
): DataStore<T> {
    return androidx.datastore.core.DataStoreFactory.create(
        serializer = serializer,
        produceFile = { dataStoreFile(fileName) }
    )
}
