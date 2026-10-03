package com.packmuleforge.carfindermvp.shared.testing

import com.packmuleforge.carfindermvp.shared.persistence.PersistedParkingRecord
import com.packmuleforge.carfindermvp.shared.platform.Capability
import com.packmuleforge.carfindermvp.shared.platform.PermissionStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A ground-truth position. */
data class TruePosition(val latitude: Double, val longitude: Double)

/** One timed input of a replay. A [Fix] and a [Heading] carry the truth; the runner adds any reading error. */
sealed interface ReplayStep {
    data class Fix(val truePosition: TruePosition, val accuracyMeters: Double?, val speedMph: Double? = null) : ReplayStep
    data class Heading(val trueHeadingDegrees: Double?) : ReplayStep
    data class Visibility(val visible: Boolean) : ReplayStep
    data class InVehicle(val inVehicle: Boolean) : ReplayStep
    data class Permission(val capability: Capability, val status: PermissionStatus) : ReplayStep
    data class Advance(val millis: Long) : ReplayStep
}

/**
 * A recorded or synthetic sequence played through the shared logic (QR-016). With [errorSeed] set, each fix is
 * reported displaced from its true position by a repeatable random error no larger than its accuracy radius.
 *
 * @requirement QR-016
 */
data class ReplayScript(
    val steps: List<ReplayStep>,
    val initialRecord: PersistedParkingRecord = PersistedParkingRecord.DEFAULT,
    val trueCar: TruePosition? = null,
    val errorSeed: Long? = null,
)

/**
 * A seeded reading error: a displacement uniform over the disk whose radius is the reported accuracy, so it is
 * never larger than that accuracy (QR-016).
 *
 * @requirement QR-016
 */
class SeededError(seed: Long) {
    private val random = Random(seed)

    fun displace(position: TruePosition, accuracyMeters: Double): TruePosition {
        val distance = accuracyMeters * sqrt(random.nextDouble())
        val direction = random.nextDouble() * 2 * PI
        val reading = Readings.readingOffset(
            northMeters = distance * cos(direction),
            eastMeters = distance * sin(direction),
            fromLatitude = position.latitude,
            fromLongitude = position.longitude,
        )
        return TruePosition(reading.latitude, reading.longitude)
    }
}
