package com.packmuleforge.carfindermvp.shared.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @requirement QR-007
 */
class NumericConstantScanTest {

    /** @requirement QR-007 */
    @Test
    fun numericConstantsLiveOnlyInCarFinderConstants() {
        val outside = SourceTree.kotlinFiles("shared/src/commonMain")
            .filter { it.name != CONSTANTS_FILE }
            .flatMap { file -> NUMERIC_CONST.findAll(file.readText()).map { "${file.name}: ${it.groupValues[1]}" } }
        assertTrue(outside.isEmpty(), "Numeric constants outside $CONSTANTS_FILE: $outside")
    }

    /** @requirement QR-007 */
    @Test
    fun carFinderConstantsDeclaresOnlySpecConstantsAndUnitConversions() {
        val file = SourceTree.kotlinFiles("shared/src/commonMain").singleOrNull { it.name == CONSTANTS_FILE }
        assertTrue(file != null, "$CONSTANTS_FILE must exist exactly once under shared/src/commonMain")
        val declared = ANY_CONST.findAll(file.readText()).map { it.groupValues[1] }.toSet()
        assertEquals(ALLOWED, declared)
    }

    private companion object {
        const val CONSTANTS_FILE = "CarFinderConstants.kt"
        val NUMERIC_CONST = Regex("""\bconst\s+val\s+(\w+)\s*(?::\s*\w+\s*)?=\s*[-+]?\d""")
        val ANY_CONST = Regex("""\bconst\s+val\s+(\w+)""")
        val ALLOWED = setOf(
            "PARKING_SPEED_THRESHOLD_MPH", "DRIVING_SPEED_THRESHOLD_MPH", "CONVERGENCE_RADIUS_METERS",
            "CONVERGENCE_SAMPLE_COUNT", "SAMPLING_INTERVAL_PARKING_MILLIS", "SAMPLING_INTERVAL_GUIDANCE_MILLIS",
            "SAMPLING_INTERVAL_IDLE_MILLIS", "ARRIVAL_CONE_HALF_ANGLE_DEGREES", "DISTANCE_UNIT_THRESHOLD_FEET",
            "FIX_STALENESS_TIMEOUT_MILLIS", "HEADING_STALENESS_TIMEOUT_MILLIS", "PARKED_RECOVERY_WINDOW_MILLIS",
            "TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS", "CONE_CONTAINMENT_TARGET", "SPEED_FILTER_WINDOW_SIZE",
            "AVAILABILITY_RECHECK_INTERVAL_MILLIS", "CONE_LENGTH_FRACTION", "SHUTDOWN_NOTICE_DURATION_MILLIS",
            "MPH_PER_METER_PER_SECOND", "FEET_PER_METER", "FEET_PER_MILE",
        )
    }
}
