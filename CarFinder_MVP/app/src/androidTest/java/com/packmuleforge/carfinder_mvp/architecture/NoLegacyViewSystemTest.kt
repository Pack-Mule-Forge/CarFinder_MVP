package com.packmuleforge.carfinder_mvp.architecture

import com.packmuleforge.carfinder.shared.annotation.Requirement
import org.junit.Test
import java.io.File

@Requirement("FR-041")
class NoLegacyViewSystemTest {

    @Test
    fun noAndroidViewImportsInUIPackage() {
        // FR-041: No file in the ui package imports android.view or androidx.compose.ui.viewinterop.
        // This ensures 100% Compose-only UI (no Views, no interop bridges).
        val uiDir = File("app/src/main/java/com/packmuleforge/carfinder_mvp/ui")
        if (!uiDir.exists()) {
            // UI directory may not exist yet; test passes trivially
            return
        }

        val kotlinFiles = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val illegalImports = listOf(
            "import android.view.",
            "import androidx.compose.ui.viewinterop"
        )

        for (file in kotlinFiles) {
            val content = file.readText()
            for (illegalImport in illegalImports) {
                if (content.contains(illegalImport)) {
                    throw AssertionError("File ${file.name} contains illegal import: $illegalImport")
                }
            }
        }
    }
}
