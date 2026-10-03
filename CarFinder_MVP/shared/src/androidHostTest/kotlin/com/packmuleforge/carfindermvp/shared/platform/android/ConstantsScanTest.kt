package com.packmuleforge.carfindermvp.shared.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @requirement FR-055, QR-005
 */
class ConstantsScanTest {

    /** @requirement FR-055 */
    @Test
    fun everyNamedConstantIsDeclaredExactlyOnceInCarFinderConstants() {
        val files = SourceTree.kotlinFiles(COMMON_MAIN)
        for (name in FR055_NAMES) {
            val declaringFiles = files.flatMap { file ->
                Regex("""\bconst\s+val\s+$name\b""").findAll(file.readText()).map { file.name }.toList()
            }
            assertEquals(listOf(CONSTANTS_FILE), declaringFiles, "$name must be declared once, in $CONSTANTS_FILE")
        }
    }

    /** @requirement QR-005 */
    @Test
    fun noTestSourceRepeatsAConstantValue() {
        val offenders = TEST_SOURCE_DIRS.flatMap { SourceTree.kotlinFiles(it) }
            .filter { it.name != SELF }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (ROBOLECTRIC_SDK_LINE.containsMatchIn(line)) return@mapIndexedNotNull null
                    val hits = NUMERIC_TOKEN.findAll(line).map { it.value.replace("_", "") }
                        .filter { it in FORBIDDEN_LITERALS }.toList()
                    if (hits.isEmpty()) null else "${SourceTree.relativePath(file)}:${index + 1} $hits"
                }
            }
        assertTrue(offenders.isEmpty(), "Constant values repeated in tests; use CarFinderConstants names:\n" +
            offenders.joinToString("\n"))
    }

    private companion object {
        const val COMMON_MAIN = "shared/src/commonMain"
        const val CONSTANTS_FILE = "CarFinderConstants.kt"
        const val SELF = "ConstantsScanTest.kt"
        val TEST_SOURCE_DIRS = listOf("shared/src/commonTest", "shared/src/androidHostTest", "app/src/test")
        val ROBOLECTRIC_SDK_LINE = Regex("""\bsdk\s*=""")
        val NUMERIC_TOKEN = Regex("""(?<![\w.])\d[\d_]*(\.\d+)?L?(?![\w.])""")
        val FORBIDDEN_LITERALS = setOf(
            "5.0", "25.0", "10.0", "45.0", "500.0", "0.90", "0.9", "0.65",
            "500L", "1000L", "2000L", "5000L", "20000L", "30000L", "180000L",
        )
        val FR055_NAMES = listOf(
            "PARKING_SPEED_THRESHOLD_MPH", "DRIVING_SPEED_THRESHOLD_MPH", "CONVERGENCE_RADIUS_METERS",
            "CONVERGENCE_SAMPLE_COUNT", "SAMPLING_INTERVAL_PARKING_MILLIS", "SAMPLING_INTERVAL_GUIDANCE_MILLIS",
            "SAMPLING_INTERVAL_IDLE_MILLIS", "ARRIVAL_CONE_HALF_ANGLE_DEGREES", "DISTANCE_UNIT_THRESHOLD_FEET",
            "FIX_STALENESS_TIMEOUT_MILLIS", "HEADING_STALENESS_TIMEOUT_MILLIS", "PARKED_RECOVERY_WINDOW_MILLIS",
            "TIME_TO_GUIDANCE_VISIBLE_TARGET_MILLIS", "CONE_CONTAINMENT_TARGET", "SPEED_FILTER_WINDOW_SIZE",
            "AVAILABILITY_RECHECK_INTERVAL_MILLIS", "CONE_LENGTH_FRACTION", "SHUTDOWN_NOTICE_DURATION_MILLIS",
        )
    }
}
