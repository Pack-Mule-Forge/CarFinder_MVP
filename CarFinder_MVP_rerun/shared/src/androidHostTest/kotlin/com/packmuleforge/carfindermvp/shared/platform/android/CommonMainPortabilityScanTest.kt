package com.packmuleforge.carfindermvp.shared.platform.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Shared domain code must have no dependency on platform APIs, so it stays portable to iOS.
 * @requirement QR-010
 */
class CommonMainPortabilityScanTest {

    private val forbidden = listOf(
        Regex("""^\s*import\s+android\."""),
        Regex("""^\s*import\s+java\."""),
        Regex("""String\.format\("""),
        Regex("""\bSystem\."""),
    )

    /** @requirement QR-010 */
    @Test
    fun commonMain_usesNoPlatformApis() {
        val sources = SourceTree.kotlinFiles(File(SourceTree.repoRoot, "shared/src/commonMain"))
        assertTrue(sources.isNotEmpty(), "no commonMain sources found under ${SourceTree.repoRoot}")
        val violations = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.any { it.containsMatchIn(line) }) "${SourceTree.relative(file)}:${index + 1}: $line"
                else null
            }
        }
        assertTrue(violations.isEmpty(), "Platform API use in commonMain:\n" + violations.joinToString("\n"))
    }
}
