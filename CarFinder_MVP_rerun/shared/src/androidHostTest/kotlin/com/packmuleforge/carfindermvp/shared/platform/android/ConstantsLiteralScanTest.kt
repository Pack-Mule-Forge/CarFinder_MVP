package com.packmuleforge.carfindermvp.shared.platform.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests must reference FR-030 constants by name, never by their literal values.
 * @requirement QR-002, FR-030
 */
class ConstantsLiteralScanTest {

    // Matches the FR-030 literal values as standalone numeric tokens. The count constants (window and sample
    // count) cannot be scanned reliably and are covered by review instead.
    private val literalPattern =
        Regex("""(?<![\w.])(5\.0|25\.0|10\.0|45\.0|500\.0|2_?000L?|5_?000L?|30_?000L?)(?![\w.])""")

    private val constantNames = listOf(
        "PARKING_SPEED_THRESHOLD_MPH",
        "DRIVING_SPEED_THRESHOLD_MPH",
        "CONVERGENCE_RADIUS_METERS",
        "CONVERGENCE_SAMPLE_COUNT",
        "PARKING_SAMPLING_INTERVAL_MILLIS",
        "ARRIVAL_HALF_ANGLE_DEGREES",
        "DISTANCE_UNIT_THRESHOLD_FEET",
        "SPEED_FILTER_WINDOW_SIZE",
        "FIX_STALENESS_TIMEOUT_MILLIS",
        "HEADING_STALENESS_TIMEOUT_MILLIS",
    )

    /** @requirement QR-002 */
    @Test
    fun testFiles_doNotContainConstantLiterals() {
        val root = SourceTree.repoRoot
        val testDirs = (File(root, "shared/src").listFiles().orEmpty().filter { it.name.endsWith("Test") }) +
            File(root, "app/src/test")
        val violations = testDirs.flatMap(SourceTree::kotlinFiles).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                literalPattern.find(line)?.let { "${SourceTree.relative(file)}:${index + 1}: ${it.value}" }
            }
        }
        assertTrue(violations.isEmpty(), "FR-030 literals found in tests:\n" + violations.joinToString("\n"))
    }

    /** @requirement FR-030 */
    @Test
    fun eachConstant_isDeclaredExactlyOnce_inCarFinderConstants() {
        val commonMain = File(SourceTree.repoRoot, "shared/src/commonMain")
        val sources = SourceTree.kotlinFiles(commonMain)
        for (name in constantNames) {
            val declarations = sources.flatMap { file ->
                Regex("""\bval\s+$name\b""").findAll(file.readText()).map { file.name }.toList()
            }
            assertEquals(listOf("CarFinderConstants.kt"), declarations, "declarations of $name")
        }
    }
}
