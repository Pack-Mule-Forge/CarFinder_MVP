package com.packmuleforge.carfindermvp.shared.domain

import com.packmuleforge.carfindermvp.shared.domain.CarFinderConstants.PARKED_RECOVERY_WINDOW_MILLIS
import com.packmuleforge.carfindermvp.shared.testing.Readings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** @requirement QR-001 */
class ParkedLocationRecoveryWindowTest {

    private val declaredAt = 1_790_000_000_000L
    private val parked = ParkedLocation(Readings.BASE_LATITUDE, Readings.BASE_LONGITUDE, Readings.GOOD_ACCURACY_METERS, declaredAt)

    /** @requirement FR-021, FR-023 */
    @Test
    fun theWindowIsOpenFromTheDeclarationThroughExactlyTheWindowLength() {
        assertTrue(parked.isRecoveryOpen(declaredAt))
        assertTrue(parked.isRecoveryOpen(declaredAt + PARKED_RECOVERY_WINDOW_MILLIS / 2))
        assertTrue(parked.isRecoveryOpen(declaredAt + PARKED_RECOVERY_WINDOW_MILLIS))
    }

    /** @requirement FR-021, FR-024 */
    @Test
    fun theWindowIsClosedOneMillisecondAfterItsEndAndBeforeTheDeclaration() {
        assertFalse(parked.isRecoveryOpen(declaredAt + PARKED_RECOVERY_WINDOW_MILLIS + 1))
        assertFalse(parked.isRecoveryOpen(declaredAt - 1))
    }
}
