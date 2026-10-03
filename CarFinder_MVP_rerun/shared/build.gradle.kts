// Shared Kotlin Multiplatform core: domain logic, engine, presenter and adapter interfaces in commonMain;
// Android adapter implementations in androidMain (research R1, R2). commonTest runs as Android host tests.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.packmuleforge.carfindermvp.shared"
        compileSdk = 37
        // @requirement QR-015
        minSdk = 26

        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":shared-testing"))
        }
        androidMain.dependencies {
            implementation(libs.play.services.location)
            implementation(libs.androidx.datastore)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}
