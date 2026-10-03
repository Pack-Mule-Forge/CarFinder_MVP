package com.packmuleforge.carfindermvp.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test j of contracts/guidance-ui.md, plus the QR-013 rules a source scan can check.
 *
 * @requirement QR-012, QR-013
 */
class UiSourceScanTest {

    private val uiDir: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        File(checkNotNull(dir), "app/src/main/kotlin/com/packmuleforge/carfindermvp/ui")
    }

    private fun offenders(pattern: Regex): List<String> {
        assertTrue(uiDir.isDirectory, "missing $uiDir")
        return uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex().filter { pattern.containsMatchIn(it.value) }
                .map { "${file.name}:${it.index + 1}: ${it.value.trim()}" }
        }.toList()
    }

    /** @requirement QR-012 */
    @Test
    fun noUiFileImportsTheAndroidViewSystem() {
        val found = offenders(Regex("""^\s*import\s+(android\.view\.|android\.widget\.|androidx\.compose\.ui\.viewinterop\.)"""))
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    /** @requirement QR-013 */
    @Test
    fun noUiFileComputesTrigonometry() {
        val found = offenders(Regex("""kotlin\.math\.(sin|cos|tan|asin|acos|atan|atan2)\b|\b(sin|cos|tan|asin|acos|atan|atan2)\("""))
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    /** @requirement QR-013 */
    @Test
    fun noUiFileCollectsAFlow() {
        val found = offenders(Regex("""\.collect\b|collectAsState"""))
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }
}
