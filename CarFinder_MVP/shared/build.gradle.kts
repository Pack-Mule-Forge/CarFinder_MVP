// Recipe for the `shared` module: Kotlin Multiplatform code (state machine, geometry, models)
// plus thin Android adapters (location, compass, permissions).

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // CHANGED: was `android.library`. AGP 9 no longer allows com.android.library together with
    // Kotlin Multiplatform; this plugin is the replacement built for exactly this combination.
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // REMOVED: the protobuf plugin. Nothing in `shared` uses protobuf; the .proto file and the
    // DataStore adapter live in the `app` module, so the protobuf plugin now lives there.
}

kotlin {
    // CHANGED: replaces the old `androidTarget { ... }` block and the `android { ... }` block.
    // These are the Android settings for this module.
    androidLibrary {
        namespace = "com.packmuleforge.carfinder.shared"
        compileSdk = 37   // same Android version the app compiles against
        minSdk = 24       // oldest phone supported (Android 7.0)

        // Runs the tests on your computer (no phone needed). Without this, tests in commonTest
        // would not run for the Android target.
        // VERIFY: check the exact name against https://developer.android.com/kotlin/multiplatform/plugin
        withHostTest { }
    }

    sourceSets {
        // commonMain = code that would work on any platform (Android now, iOS later).
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        // commonTest = tests for that shared code.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // androidMain = Android-only code (the adapters).
        androidMain.dependencies {
            implementation(libs.play.services.location)
            // REMOVED: androidx.datastore + protobuf-kotlin (unused here; they belong to `app`).
        }
        // REMOVED: androidUnitTest / junit. There are no Android-only unit tests in this module,
        // and AGP 9 renames that source set (androidHostTest) anyway.
    }
}

// REMOVED: the old `android { ... }` block, `kotlinOptions`, and `protobuf { ... }` block.
// Their settings moved into `androidLibrary { ... }` above or into the `app` module.
