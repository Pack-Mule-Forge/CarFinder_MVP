package com.packmuleforge.carfinder_mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.packmuleforge.carfinder.shared.annotation.Requirement
import com.packmuleforge.carfinder.shared.model.GuidanceViewState
import com.packmuleforge.carfinder.shared.platform.AndroidPermissionController
import com.packmuleforge.carfinder.shared.platform.PermissionRequester
import com.packmuleforge.carfinder_mvp.permission.PermissionFlowCoordinator
import com.packmuleforge.carfinder_mvp.ui.GuidanceDisplay
import com.packmuleforge.carfinder_mvp.ui.GuidanceViewModel
import com.packmuleforge.carfinder_mvp.ui.StatusMessage
import com.packmuleforge.carfinder_mvp.ui.theme.CarFinder_MVPTheme
import kotlin.coroutines.resume

/**
 * Main activity for Car Finder. Displays the default view showing parking state and guidance.
 * The view is read-only; it observes the state machine output and renders it (FR-014, FR-042).
 *
 * T110 (CR-5 construction fix): AndroidPermissionController is built with only an Application
 * Context and cannot itself show a dialog. This Activity owns the live
 * androidx.activity.result.ActivityResultLauncher and bridges it to the controller via
 * [PermissionRequester], attached in onCreate before the permission flow ever runs.
 */
@Requirement("FR-017", "FR-018", "FR-041", "FR-042", "FR-045", "FR-046")
class MainActivity : ComponentActivity() {
    // Registered as a field so it happens during construction, well before the Activity reaches
    // STARTED, as the Activity Result API requires.
    private val permissionRequester = ActivityPermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val application = applicationContext as CarFinderApplication
        (application.permissionController as AndroidPermissionController).requester = permissionRequester

        setContent {
            CarFinder_MVPTheme {
                DefaultView(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * T110: bridges [PermissionRequester] to a live Activity Result launcher. One permission is
 * requested at a time; [mutex] serializes calls since only one launch can be in flight on the
 * launcher at once (PermissionFlowCoordinator already requests capabilities sequentially, so this
 * is a safety net, not the primary serialization mechanism).
 */
private class ActivityPermissionRequester(private val activity: ComponentActivity) : PermissionRequester {
    private val mutex = Mutex()
    private var pendingContinuation: CancellableContinuation<Boolean>? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingContinuation?.resume(granted)
        pendingContinuation = null
    }

    override suspend fun requestPermission(permission: String): Boolean = mutex.withLock {
        suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            launcher.launch(permission)
        }
    }

    override fun shouldShowRationale(permission: String): Boolean =
        activity.shouldShowRequestPermissionRationale(permission)
}

/**
 * FR-041: the default view is built entirely in Jetpack Compose, no legacy View-system component.
 * FR-042: it renders from state exposed by the shared domain layer; no view state is computed or
 * owned here (see [GuidanceViewModel] and [com.packmuleforge.carfinder.shared.view.GuidanceViewStateCalculator]).
 */
@Requirement("FR-041", "FR-042")
@Composable
fun DefaultView(modifier: Modifier = Modifier) {
    // CR-12/T115: GuidanceViewModel has no no-arg constructor, so it needs an explicit factory
    // that reads the app-wide singletons off CarFinderApplication.
    val application = LocalContext.current.applicationContext as CarFinderApplication
    val factory = viewModelFactory {
        initializer {
            GuidanceViewModel(
                repository = application.repository,
                locationProvider = application.locationProvider,
                headingProvider = application.headingProvider,
                clock = application.clock
            )
        }
    }
    val viewModel: GuidanceViewModel = viewModel(factory = factory)
    val viewState = viewModel.viewState.collectAsState()

    // T110/T111 (FR-045/FR-046, CR-5 fix): run the permission sequence every time the default
    // view appears; PermissionFlowCoordinator itself skips anything already declined this
    // session, and starts the background service automatically the moment location is granted.
    val coordinator: PermissionFlowCoordinator = application.permissionFlowCoordinator
    LaunchedEffect(coordinator) {
        coordinator.runFlow()
    }
    val locationGranted = coordinator.locationGranted.collectAsState()

    // FR-044/FR-046: the ephemeral location/heading path only collects while this view is
    // STARTED *and* location permission is granted. While it is not granted, currentFix and
    // deviceHeading in GuidanceViewModel simply never leave their seeded `null` values, which
    // GuidanceViewStateCalculator's existing FR-030 fallback already turns into NoParkedLocation
    // whenever the persisted state is PARKED — the same path used for a stale/missing live fix.
    // No new parameter or priority logic was added to the calculator or the ViewModel for this;
    // gating the start of collection here was the smallest hook (see final report for the one
    // known edge case this does not cover: a state of DRIVING/PARKING persisted from before
    // permission was revoked keeps showing its own message rather than snapping to FINDING,
    // which is deferred alongside the other degraded-permission modes already out of MVP scope).
    // The persisted repository path inside the ViewModel keeps running regardless (FR-014).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, locationGranted.value) {
        if (!locationGranted.value) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startEphemeralCollection()
            try {
                awaitCancellation()
            } finally {
                viewModel.stopEphemeralCollection()
            }
        }
    }

    // Exhaustive when ensures all GuidanceViewState variants are handled (FR-018)
    when (val state = viewState.value) {
        is GuidanceViewState.Driving -> {
            StatusMessage(
                text = "Driving - Waiting to Park",
                modifier = modifier
            )
        }

        is GuidanceViewState.NoParkedLocation -> {
            StatusMessage(
                text = stringResource(R.string.state_no_location),
                modifier = modifier
            )
        }

        is GuidanceViewState.ParkingSoon -> {
            StatusMessage(
                text = "Sensing you will be Parking Soon.",
                modifier = modifier
            )
        }

        is GuidanceViewState.Guidance -> {
            val promptDismissed = viewModel.arrivalPromptDismissed.collectAsState()
            GuidanceDisplay(
                distanceMeters = androidx.compose.runtime.derivedStateOf { state.distanceMeters },
                formattedDistance = androidx.compose.runtime.derivedStateOf { state.formattedDistance },
                displayBearingDegrees = androidx.compose.runtime.derivedStateOf { state.displayBearingDegrees },
                coneHalfAngleRadians = androidx.compose.runtime.derivedStateOf { state.coneHalfAngleRadians },
                hasArrived = androidx.compose.runtime.derivedStateOf { state.hasArrived },
                promptDismissed = promptDismissed,
                onArrivalAnswered = { yesClicked ->
                    // FR-033: Answering the prompt is local UI state only, held in the
                    // ViewModel so it survives recomposition/rotation. The state machine and
                    // Parked Location are unchanged.
                    viewModel.onArrivalAnswered(yesClicked)
                },
                modifier = modifier
            )
        }
    }
}