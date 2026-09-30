# Car Finder

Car Finder is an Android app that notices when you have parked, remembers where the car is, and guides you back
to it. It detects parking automatically from your phone's filtered speed and a cluster of converging location
fixes, with no "I parked" button. It then shows a cone that points toward the car and widens when the position
is uncertain, along with the remaining distance. When the uncertainty covers the remaining distance, it says
"You have arrived". Everything runs on the device, and detection keeps running in the background with a
persistent notification.

The domain logic lives in a Kotlin Multiplatform shared core so that a future iOS app can reuse it. The Android
app adds native adapters (location, compass, activity recognition, storage, permissions) and a Jetpack Compose UI.

## Project layout

| Path | What it holds |
|---|---|
| `shared/` | KMP module. `commonMain` has the state machine, guidance math, presenter and adapter interfaces; `androidMain` has the Android adapters. |
| `shared-testing/` | Fakes for every platform adapter, used by `:shared` and `:app` tests. |
| `app/` | Android app: foreground service, boot receiver, notification and the Compose home screen. |
| `benchmark/` | Macrobenchmark that measures guidance frame timing on a device (FR-027). |
| `tools/traceability/` | Script that produces the requirement traceability report, plus its Pester tests. |
| `specs/002-park-detect-guidance/` | Spec, plan, research, data model, contracts, quickstart and tasks. |

## Prerequisites

- JDK 17 or later. Android Studio's bundled JBR works; set `JAVA_HOME` to it if `java` is not on your PATH.
- Android SDK with the platform named by `compileSdk` (API 37). Point `local.properties` at it (`sdk.dir=...`).
- PowerShell 5.1 or later, and Pester 5 or later for the traceability tests:
  `Install-Module Pester -MinimumVersion 5.0 -Scope CurrentUser`.
- For device checks: an Android phone on API 34 or later with GPS and a magnetometer, and `adb`.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test

```powershell
# Shared domain, engine, presenter and Android adapter tests (JVM, via Robolectric)
.\gradlew.bat :shared:testAndroidHostTest

# App tests: Compose semantics UI tests, service, boot receiver, source scans
.\gradlew.bat :app:testDebugUnitTest

# Traceability tool tests
Invoke-Pester tools\traceability\tests
```

None of these need an emulator.

**Frame timing (FR-027)** needs a connected physical device:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
.\tools\benchmark\Assert-FrameBudget.ps1
```

## Traceability report

Every requirement in the spec is linked to code and tests by KDoc `@requirement FR-xxx` tags (constitution
Principle II). To regenerate the report:

```powershell
.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps
```

It writes `specs/002-park-detect-guidance/traceability.md` and exits 1 if any requirement is untested or
untraced, or if any tag points at a requirement that does not exist.

## Install on a phone

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On first launch, grant location, notification and activity-recognition access. Then, in system Settings, set
location access to **Allow all the time** so detection can restart after a reboot. Without it, the notification
explains what is missing, and detection resumes the next time the app is opened.

## Further documentation

- [Feature spec](specs/002-park-detect-guidance/spec.md)
- [Implementation plan](specs/002-park-detect-guidance/plan.md) and [research decisions](specs/002-park-detect-guidance/research.md)
- [Contracts](specs/002-park-detect-guidance/contracts/)
- [Quickstart and validation guide](specs/002-park-detect-guidance/quickstart.md)
- [Project constitution](.specify/memory/constitution.md)
