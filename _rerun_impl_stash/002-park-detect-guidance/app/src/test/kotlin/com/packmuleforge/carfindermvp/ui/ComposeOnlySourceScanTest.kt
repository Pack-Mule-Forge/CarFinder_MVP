package com.packmuleforge.carfindermvp.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The home screen is Compose-only, and no test or benchmark fakes ship in the release source set.
 * @requirement QR-008
 */
class ComposeOnlySourceScanTest {

    private val main = File("src/main").absoluteFile

    private fun kotlinFiles(dir: File) = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun violations(files: List<File>, patterns: List<Regex>) = files.flatMap { file ->
        file.readLines().mapIndexedNotNull { i, line ->
            if (patterns.any { it.containsMatchIn(line) }) "${file.relativeTo(main)}:${i + 1}: ${line.trim()}" else null
        }
    }

    /** @requirement QR-008 */
    @Test
    fun mainSources_useNoLegacyViewSystem() {
        val sources = kotlinFiles(main)
        assertTrue(sources.isNotEmpty(), "no sources under $main")
        val all = violations(
            sources,
            listOf(Regex("""\bAndroidView\("""), Regex("""\bsetContentView\("""), Regex("""^\s*import\s+android\.widget\.""")),
        )
        val ui = violations(kotlinFiles(File(main, "kotlin/com/packmuleforge/carfindermvp/ui")),
            listOf(Regex("""^\s*import\s+android\.view\.View\b""")))
        assertTrue((all + ui).isEmpty(), "legacy View usage:\n" + (all + ui).joinToString("\n"))
        assertFalse(File(main, "res/layout").exists(), "res/layout must not exist")
    }

    /** @requirement QR-008 */
    @Test
    fun mainSources_containNoTestOrBenchmarkFakes() {
        val found = violations(
            kotlinFiles(main),
            listOf(Regex("""(?i)synthetic"""), Regex("""InMemoryParkingStore"""), Regex("""shared\.testing""")),
        )
        assertTrue(found.isEmpty(), "test/benchmark code in main:\n" + found.joinToString("\n"))
    }
}
