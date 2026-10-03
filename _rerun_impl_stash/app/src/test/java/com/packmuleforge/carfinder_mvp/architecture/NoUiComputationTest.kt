package com.packmuleforge.carfinder_mvp.architecture

import com.packmuleforge.carfinder.shared.annotation.Requirement
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FR-042: the default view MUST render from state exposed by the shared domain layer; view state
 * MUST NOT be computed or owned inside the UI layer. This asserts the absence of the tell-tale
 * signs of that computation creeping back into `:app`'s `ui` package.
 */
@Requirement("FR-042")
class NoUiComputationTest {

    private val uiDir = File("src/main/java/com/packmuleforge/carfinder_mvp/ui")

    private val forbiddenTokens = listOf(
        "atan2(",
        "atan(",
        "ParkingConstants.PARKING_SPEED_THRESHOLD_MPH",
        "ParkingConstants.PARKING_SPEED_THRESHOLD_MPS",
        "ParkingConstants.DRIVING_SPEED_THRESHOLD_MPH",
        "ParkingConstants.DRIVING_SPEED_THRESHOLD_MPS",
        "ParkingConstants.CONVERGENCE_RADIUS_METERS",
        "ParkingConstants.CONVERGENCE_SAMPLE_COUNT",
        "ParkingConstants.PARKING_SAMPLE_INTERVAL_MILLIS",
        "ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_DEGREES",
        "ParkingConstants.ARRIVAL_CONE_HALF_ANGLE_RADIANS",
        "ParkingConstants.DISTANCE_UNIT_THRESHOLD_FEET",
        "ParkingConstants.DISTANCE_UNIT_THRESHOLD_METERS",
        "ParkingConstants.FIX_STALENESS_TIMEOUT_MILLIS"
    )

    @Test
    fun uiPackageDoesNotComputeAngleMathOrReferenceThresholdConstants() {
        assertTrue(uiDir.exists(), "Expected the ui package directory to exist at $uiDir")

        val kotlinFiles = uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(kotlinFiles.isNotEmpty(), "Expected at least one Kotlin file under $uiDir")

        for (file in kotlinFiles) {
            val content = file.readText()
            for (token in forbiddenTokens) {
                assertFalse(
                    content.contains(token),
                    "${file.name} references '$token' — angle math and threshold constants belong in :shared (FR-042)"
                )
            }
        }
    }

    @Test
    fun guidanceViewModelContainsNoWhenOverParkingState() {
        val viewModelFile = File(uiDir, "GuidanceViewModel.kt")
        assertTrue(viewModelFile.exists(), "Expected GuidanceViewModel.kt at $viewModelFile")

        val content = viewModelFile.readText()
        val whenExpressionRegex = Regex("""\bwhen\s*[({]""")
        assertFalse(
            whenExpressionRegex.containsMatchIn(content),
            "GuidanceViewModel must not branch over ParkingState or any other value; " +
                "all such logic belongs in GuidanceViewStateCalculator (FR-042)"
        )
    }
}
