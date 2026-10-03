// Test-only Kotlin Multiplatform library: fakes for every adapter, reading builders and the scripted-replay
// harness, shared by :shared tests and :app tests (research R1, QR-003).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.packmuleforge.carfindermvp.shared.testing"
        compileSdk = 37
        // @requirement QR-015
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.kotlinx.coroutines.test)
        }
    }
}
