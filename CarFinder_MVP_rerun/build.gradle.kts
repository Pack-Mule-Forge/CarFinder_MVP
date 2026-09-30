// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// This file announces which plugins exist for the whole project. `apply false` means "make this plugin known,
// with its version, but don't apply it in THIS file". Each module switches on the plugins it needs.
//
// ORDER MATTERS: Kotlin Multiplatform is listed before the Android plugins because AGP 9 bundles its own Kotlin
// support, and loading it first can cause the "plugin is already on the classpath with an unknown version" error.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    // Since AGP 9, com.android.library refuses to work with Kotlin Multiplatform, so KMP modules use this plugin.
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Robolectric reads java.io/java.lang internals that recent JDKs (the build runs on JDK 25) close by default,
// failing with "Failed to interact with raw FileDescriptor internals". Open them for every JVM test task.
subprojects {
    tasks.withType<Test>().configureEach {
        jvmArgs(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )
        // Robolectric URL-encodes the local Maven path, so a space in the user profile ("C:\Users\First Last")
        // turns into "%20" and the native runtime jar is "not found". Keep its artifact cache on a path under the
        // repository instead.
        systemProperty("maven.repo.local", rootProject.layout.buildDirectory.dir("robolectric-m2").get().asFile.path)
    }
}
