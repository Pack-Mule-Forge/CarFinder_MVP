package com.packmuleforge.carfinder_mvp.ui

import com.packmuleforge.carfinder.shared.annotation.Requirement
import org.junit.Test

/**
 * T109. Source-only per this task's Step 4: written and left un-run, matching the existing
 * placeholder convention in this file's siblings (StatusMessageTest, ArrivalDismissTest, etc.) —
 * there is no emulator in this session. Not marked as verified.
 */
@Requirement("FR-046")
class PermissionDeniedViewTest {

    @Test
    fun defaultViewShowsFindingWhenLocationPermissionNotGranted() {
        // FR-046: with location permission not granted (deny in the system dialog, or launch
        // with permissions never granted), DefaultView must render the "Parked location
        // unavailable." message (StatusMessage with state_no_location), never GuidanceDisplay.
        // Once Compose test infra is wired (ComposeTestRule + a way to fake permission grant
        // state for an instrumented run), assert:
        //   composeTestRule.onNodeWithText(stringResource(R.string.state_no_location)).assertIsDisplayed()
        //   composeTestRule.onNodeWithTag("guidance_display").assertDoesNotExist()
        // Placeholder until Compose test setup is complete, per this repo's existing convention.
    }

    @Test
    fun guidanceDisplayNeverAppearsWhileLocationPermissionIsDenied() {
        // FR-046: even if a Parked Location and a stale PARKED state are already persisted from
        // before permission was revoked, guidance (the cone/GuidanceDisplay) must never render
        // while location permission is not currently granted. MainActivity's DefaultView gates
        // GuidanceViewModel.startEphemeralCollection() on PermissionFlowCoordinator.locationGranted,
        // so currentFix/deviceHeading never leave null and GuidanceViewStateCalculator's existing
        // FR-030 fallback yields NoParkedLocation.
    }

    @Test
    fun permissionPromptReturnsOnNextLaunchAfterDecline() {
        // FR-045/SC-014: declining location on one launch must not suppress the request forever —
        // relaunching the app (a new process, i.e. a new session) must ask again. Within the same
        // session it must not repeat (covered at the unit level by PermissionFlowTest, T108).
    }
}
