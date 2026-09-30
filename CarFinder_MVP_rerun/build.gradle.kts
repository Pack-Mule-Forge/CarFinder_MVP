// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// This file is the "site manager": it announces which tools (plugins) exist for the whole project.
// `apply false` means "make this tool known, with its version, but don't use it in THIS file".
// Each module (app, shared) then switches on the tools it needs in its own build.gradle.kts.
//
// ORDER MATTERS: Kotlin Multiplatform and protobuf are listed before the Android plugins because
// AGP 9 bundles its own Kotlin support, and loading it first can cause the
// "plugin is already on the classpath with an unknown version" error.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.android.application) apply false
    // CHANGED: was libs.plugins.android.library. Since AGP 9 that plugin refuses to work together
    // with Kotlin Multiplatform, so the shared module uses this new plugin instead.
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
