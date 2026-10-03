package com.packmuleforge.carfindermvp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineStartOwnershipScanTest {

    /** @requirement FR-052 */
    @Test
    fun onlyTheForegroundServiceStartsTheEngine() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        val main = File(checkNotNull(dir), "app/src/main")
        val callers = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("engine.start(") }
            .map { it.relativeTo(main).invariantSeparatorsPath }.toList()
        assertEquals(listOf("kotlin/com/packmuleforge/carfindermvp/service/ParkingDetectionService.kt"), callers)
    }
}
