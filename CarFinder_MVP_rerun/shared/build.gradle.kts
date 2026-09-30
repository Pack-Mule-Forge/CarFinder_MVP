plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.packmuleforge.carfindermvp.shared"
        compileSdk = 37
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
            implementation(libs.androidx.core.ktx)
            // ActivityResultRegistry for AndroidPermissionController (research R6).
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.fragment.ktx)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
