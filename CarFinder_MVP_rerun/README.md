# Car Finder MVP — Automatic Park Detection and Return Guidance

Car Finder is an Android application that automatically detects when you've parked your vehicle and later guides you back to it by displaying a real-time cone on your screen showing the direction and distance to your parked location.

## Features

- **Automatic Parking Detection**: Uses GPS and device speed sensors to detect when you've parked (3 consecutive converged GPS readings within 10 meters)
- **Background Operation**: Runs as a foreground service, independent of app foreground state
- **Real-Time Guidance**: Displays an interactive cone showing direction to your parked car, accounting for:
  - Your device heading (compass)
  - GPS uncertainty (widening the cone as accuracy degrades)
  - Magnetic declination correction
- **Arrival Recognition**: Automatically detects when you're close enough to the vehicle and switches to a confirmation prompt
- **Persistent State**: Survives process death and device restart via Proto DataStore
- **Drive-Away Reset**: Automatically clears the stored location when you drive away, preventing stale locations

## Architecture

This is a **Kotlin Multiplatform Mobile (KMP)** application with a shared domain layer and Android-specific adapters:

- **`shared/src/commonMain`**: Pure Kotlin business logic (geomatics, state machine, core models)
- **`shared/src/androidMain`**: Platform adapters (Clock, LocationProvider, HeadingProvider, PermissionController, ActivityRecognizer)
- **`app/src/main`**: Android-specific UI (Jetpack Compose, 100% Views-free)
- **`app/src/android{Test,Device}Test`**: Integration and device tests

## Prerequisites

- Android Studio (AGP 9+)
- JDK 17+
- Android SDK (API 24+)
- A device or emulator running Android 6.0+
- For testing: enabling mock locations and development settings

## Installation

1. Clone the repository
2. Open in Android Studio
3. Gradle will automatically fetch dependencies (see `gradle/libs.versions.toml`)
4. Grant required permissions when prompted:
   - `ACCESS_FINE_LOCATION` (precise GPS)
   - `ACCESS_BACKGROUND_LOCATION` (location access in background)
   - `ACTIVITY_RECOGNITION` (detect vehicle transitions)

## How to Run Locally

### Build the APK
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/CarFinder_MVP-debug.apk
```

### Run on Device/Emulator
1. Launch the app
2. Grant location and activity recognition permissions
3. Start driving
4. Once parked (speed drops below threshold), the app will begin detecting convergence
5. Once 3 GPS readings converge within 10 meters, the location is locked and the state transitions to PARKED
6. While parked, you can navigate away and the app will guide you back with the cone display
7. As you approach the car, the cone widens → shrinks based on your proximity and GPS accuracy
8. At arrival (cone half-angle ≥ 45°), a confirmation prompt appears

## How to Run Tests

### Run all tests (shared + Android)
```bash
./gradlew allTests
```

### Run just shared (Kotlin Multiplatform) tests
```bash
./gradlew :shared:allTests
```

### Run Android unit tests
```bash
./gradlew :app:testDebugUnitTest
```

### Run Android instrumented tests (device required)
```bash
./gradlew :app:connectedAndroidTest
```

## Quickstart Scenarios (for manual testing)

See [specs/001-park-detect-guidance/quickstart.md](specs/001-park-detect-guidance/quickstart.md) for a complete suite of manual test scenarios covering:

- Scenario 1-3: Permission handling
- Scenario 4-7: Core parking detection and guidance
- Scenario 8-10: Edge cases and performance

## How to Build a Release Artifact

```bash
./gradlew assembleRelease
# Signed APK is generated at app/build/outputs/apk/release/CarFinder_MVP-release-unsigned.apk
# For distribution, sign with your release keystore:
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore path/to/release.keystore \
  CarFinder_MVP-release-unsigned.apk release_alias
```

## Project Structure

```
.
├── app/                              # Android app module
│   ├── src/main/
│   │   ├── java/.../ui/             # Jetpack Compose UI (Guidanc eDisplay, etc.)
│   │   ├── java/.../service/        # ParkingDetectionService
│   │   ├── java/.../adapter/        # DataStore, LocationProvider, etc.
│   │   └── AndroidManifest.xml
│   ├── src/androidTest/             # Instrumented tests (Compose, UI)
│   └── src/test/                    # Unit tests (repository, adapters)
├── shared/                           # Kotlin Multiplatform library
│   ├── src/commonMain/
│   │   ├── kotlin/.../state/        # ParkingStateMachine, ConvergenceWindow
│   │   ├── kotlin/.../geo/          # Geodesy, ConeGeometry, DistanceFormatter
│   │   ├── kotlin/.../model/        # Domain models (GeoPoint, ParkedLocation)
│   │   ├── kotlin/.../repository/   # ParkedLocationRepository interface
│   │   ├── kotlin/.../platform/     # Expect classes (Clock, LocationProvider, etc.)
│   │   └── kotlin/.../annotation/   # @Requirement traceability
│   ├── src/commonTest/              # Shared unit tests
│   ├── src/androidMain/             # Android platform adapters
│   └── src/androidTest/             # Android-specific tests
├── gradle/                           # Gradle configuration
│   └── libs.versions.toml           # Dependency versions
├── tools/                            # Development tools
│   └── traceability/
│       └── Get-TraceabilityReport.ps1  # Requirement traceability scanner
├── specs/                            # Specification documents
│   └── 001-park-detect-guidance/
│       ├── spec.md                  # Functional requirements (FR-001 through FR-042)
│       ├── plan.md                  # Implementation plan and architecture
│       ├── tasks.md                 # Detailed task breakdown
│       ├── data-model.md            # Entity relationships
│       ├── quickstart.md            # Manual testing scenarios
│       ├── research.md              # Technical decisions and constraints
│       └── contracts/               # Integration contracts
└── README.md                         # This file
```

## Architecture Decisions

### Why Kotlin Multiplatform?
The shared domain layer (state machine, geomatics, convergence detection) is platform-agnostic and benefits from being testable without Android dependencies. KMP allows this without introducing extra compilation steps.

### Why 100% Jetpack Compose?
The UI is simple and real-time; Compose's declarative nature is a natural fit, and avoiding the legacy View system (FR-041) eliminates an entire category of bugs.

### Why Proto DataStore?
Proto DataStore provides atomic, strongly-typed persistence of complex state (parking state + location + timestamp), surviving process death without additional boilerplate.

### Why All-Pairwise Convergence?
The spec requires "3 consecutive samples converge within a 10-meter radius." The implementation interprets this strictly: all three samples must be within 10 meters of each other (not just the first), which is more robust to GPS noise.

## Traceability & Requirements

Every requirement (FR-001 through FR-042, SC-001 through SC-010) is mapped to implementing code and verifying tests via `@Requirement` annotations.

To generate a traceability report:

```powershell
./tools/traceability/Get-TraceabilityReport.ps1
```

The report identifies:
- Fully traced requirements (implementation + test)
- Implemented but untested code (violates FR-039)
- Specified but unimplemented features (violates FR-038)
- Orphaned annotations (violates FR-040)

See [specs/001-park-detect-guidance/contracts/traceability-report.md](specs/001-park-detect-guidance/contracts/traceability-report.md) for details.

## Constants & Thresholds

All thresholds are defined in a single location: `shared/src/commonMain/.../constants/ParkingConstants.kt`.

- **PARKING_SPEED_THRESHOLD_MPS**: 2.2352 m/s (≈5 mph) — maximum speed for "parked"
- **DRIVING_SPEED_THRESHOLD_MPS**: 11.176 m/s (≈25 mph) — minimum speed to transition from PARKED/FINDING to DRIVING
- **CONVERGENCE_RADIUS_METERS**: 10 m — all three samples must be within this radius
- **CONVERGENCE_SAMPLE_COUNT**: 3 — required number of converged samples
- **ARRIVAL_CONE_HALF_ANGLE_RADIANS**: π/4 (45°) — cone half-angle at which arrival is detected
- **DISTANCE_UNIT_THRESHOLD_METERS**: 152.4 m (500 ft) — above this, display distance in miles; below, in feet

## Battery & Performance

- **Battery**: Service runs at reduced update frequency while parked (~60s intervals) to minimize drain
- **Frame Timing**: Compose canvas rendering is capped to device refresh rate (typically 60 Hz)
- **Memory**: State machine and repository keep a single ParkedLocation in memory; no caching of history

## Permissions

The app requests:

- `ACCESS_FINE_LOCATION` — required for parking detection
- `ACCESS_BACKGROUND_LOCATION` — to keep detecting while app is backgrounded (API 30+)
- `ACTIVITY_RECOGNITION` — to infer vehicle state (optional; app degrades gracefully if denied)
- `INTERNET` — none required (no network dependency; see Scenario 9)
- `BOOT_COMPLETED` — to restart the service on device boot

## Limitations

- **Single Location**: Only one Parked Location can be active at a time. If you park again, the previous location is forgotten.
- **No History**: No list of past locations; only the current one is retained.
- **GPS-Dependent**: Accuracy degrades in tunnels, canyons, or areas with poor satellite coverage.
- **No Offline Maps**: The cone is relative to true north (via compass); visual landmarks around you provide context.

## Support & Feedback

For issues, feature requests, or architectural questions, see:

- **Specification**: [specs/001-park-detect-guidance/spec.md](specs/001-park-detect-guidance/spec.md)
- **Technical Plan**: [specs/001-park-detect-guidance/plan.md](specs/001-park-detect-guidance/plan.md)
- **Task Breakdown**: [specs/001-park-detect-guidance/tasks.md](specs/001-park-detect-guidance/tasks.md)
- **Research & Decisions**: [specs/001-park-detect-guidance/research.md](specs/001-park-detect-guidance/research.md)

---

**Status**: MVP Complete (Phases 1–7 implemented)  
**Last Updated**: 2026-09-19  
**License**: See LICENSE file
