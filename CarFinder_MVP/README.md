# Car Finder

Car Finder is an Android app that notices when you have parked, remembers where the car is, and guides you back to
it. It detects parking from the phone's smoothed speed and three location readings that settle in one place, with no
"I parked" button, and quietly corrects a premature "parked" if you settle somewhere else within a short window. To
guide you back it shows a cone that points at the car and widens when either position is uncertain, with the
distance in the middle; when the uncertainty covers the remaining distance it says "You have arrived" and asks
"Do you see your car?". Driving away clears the old location. Everything runs on the device, and detection keeps
running in the background behind a persistent notification.

The domain logic lives in a Kotlin Multiplatform shared core so a future iOS app can reuse it. The Android app adds
native adapters (location, heading, activity recognition, storage, permissions) and a Jetpack Compose UI.

## Project layout

| Path | What it holds |
|---|---|
| `shared/` | KMP module. `commonMain`: state machine, recovery, sampling policy, guidance math, engine, presenter, adapter interfaces. `androidMain`: the Android adapters and the platform factory. |
| `shared-testing/` | Fakes for every adapter, reading builders and the scripted-replay harness, used by `:shared` and `:app` tests. |
| `app/` | Android app: foreground detection service, boot receiver, notification and the Compose home screen. |
| `tools/traceability/` | Script that produces the requirement traceability report, its Pester tests, and the declared no-code list. |
| `specs/003-park-detect-guidance/` | Spec, plan, research, data model, contracts, quickstart, tasks, findings ledger and validation results. |

## Prerequisites

- A JDK to launch Gradle, with `JAVA_HOME` set to it. Android Studio's bundled runtime works. Gradle provisions its
  own daemon toolchain (JDK 25, see `gradle/gradle-daemon-jvm.properties`).
- Android SDK with platform 37. Point `local.properties` at it (`sdk.dir=...`).
- PowerShell 5.1 or later and Pester 5 or later for the tooling tests:
  `Install-Module Pester -MinimumVersion 5.0 -Scope CurrentUser`.
- For device checks: an emulator or phone on Android 8.0 (API 26) or later, and `adb`. Field checks need a phone
  with GPS and a compass.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test

```powershell
# Shared domain, engine, presenter, replay and Android adapter tests (JVM, Robolectric for the adapters)
.\gradlew.bat :shared:testAndroidHostTest

# App tests: Compose semantics UI tests, service, boot receiver, Activity and permission flows, source scans
.\gradlew.bat :app:testDebugUnitTest

# Lint
.\gradlew.bat :app:lintDebug

# Traceability tool and findings-ledger tests
Invoke-Pester tools\traceability\tests
```

None of these need an emulator. Operating-system behavior (permission prompts, background start, reboot, process
death) is checked on an emulator or device with the steps in the
[quickstart](specs/003-park-detect-guidance/quickstart.md#5-operating-system-behavior-emulator-or-device-required-not-optional);
the latest results are in [validation-results.md](specs/003-park-detect-guidance/validation-results.md).

## Traceability report

Every requirement in the spec is linked to code and tests by `@requirement FR-xxx` / `QR-xxx` tags (constitution
Principle II). To regenerate the report:

```powershell
.\tools\traceability\Get-TraceabilityReport.ps1 -FailOnGaps
```

It writes `specs/003-park-detect-guidance/traceability.md` and exits 1 if a requirement has no code annotation
(and is not on the declared no-code list), has code but no test, or if a tag names a requirement the spec does not
define.

## Install on a phone

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On first launch the app asks, one at a time, for location, background location ("Allow all the time"), physical
activity and notifications. Location and notifications are required: denying either shows a confirmation that
Car Finder cannot run without it; choosing **Close** closes the app, and **Allow** or back asks again. With only
"While using the app" location, detection runs while the app has been opened since the last restart, and the
notification asks for "Allow all the time" so it can restart on its own after a reboot.

## Further documentation

- [Feature spec](specs/003-park-detect-guidance/spec.md)
- [Implementation plan](specs/003-park-detect-guidance/plan.md) and [research decisions](specs/003-park-detect-guidance/research.md)
- [Data model](specs/003-park-detect-guidance/data-model.md) and [contracts](specs/003-park-detect-guidance/contracts/)
- [Quickstart and validation guide](specs/003-park-detect-guidance/quickstart.md)
- [Tasks](specs/003-park-detect-guidance/tasks.md) and [findings ledger](specs/003-park-detect-guidance/analysis-findings.md)
- [Project constitution](.specify/memory/constitution.md)
