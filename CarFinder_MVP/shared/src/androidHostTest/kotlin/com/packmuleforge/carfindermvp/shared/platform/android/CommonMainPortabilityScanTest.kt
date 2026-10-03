package com.packmuleforge.carfindermvp.shared.platform.android

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * @requirement QR-014
 */
class CommonMainPortabilityScanTest {

    /** @requirement QR-014 */
    @Test
    fun commonCodeImportsNoPlatformApi() {
        val offenders = listOf("shared/src/commonMain", "shared-testing/src/commonMain")
            .flatMap { SourceTree.kotlinFiles(it) }
            .flatMap { file ->
                file.readLines().filter { PLATFORM_IMPORT.containsMatchIn(it) }
                    .map { "${SourceTree.relativePath(file)}: ${it.trim()}" }
            }
        assertTrue(offenders.isEmpty(), "Platform imports in common code:\n" + offenders.joinToString("\n"))
    }

    private companion object {
        val PLATFORM_IMPORT = Regex("""^\s*import\s+(android|androidx|java)\.""")
    }
}
