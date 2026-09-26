plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // ADDED: protobuf is the "translator" that reads src/main/proto/parking_data.proto and
    // generates the code used to save the parked location. The .proto file lives in this
    // module, so the translator belongs here (it was wrongly attached to `shared` before).
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.packmuleforge.carfinder_mvp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.packmuleforge.carfinder_mvp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ADDED: gives MainActivity the Compose viewModel() function (was an unresolved reference).
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ADDED (T104): non-deprecated androidx.lifecycle.compose.LocalLifecycleOwner, used together
    // with Lifecycle.repeatOnLifecycle to scope guidance's ephemeral location/heading collection
    // to STARTED (FR-044).
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.proto)
    // ADDED: runtime library needed by the code protobuf generates (moved here from `shared`).
    implementation(libs.protobuf.kotlin)
    testImplementation(libs.junit)
    // ADDED: some app unit tests use kotlin.test.Test / assertTrue; kotlin-test-junit maps them onto JUnit4.
    testImplementation(libs.kotlin.test.junit)
    // ADDED: runTest { } for coroutine-based app tests (e.g. the DataStore repository test).
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// ADDED: protobuf settings (moved here from `shared`).
// `protoc` is the compiler that turns the .proto file into Java and Kotlin code.
// Its version (3.24.4) is NOT the same thing as the Gradle plugin version (0.9.5).
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.4"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
        }
    }
}
