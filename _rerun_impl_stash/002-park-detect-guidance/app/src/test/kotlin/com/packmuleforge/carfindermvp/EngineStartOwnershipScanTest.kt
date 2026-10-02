package com.packmuleforge.carfindermvp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Only ParkingDetectionService's startup path may start the engine's detection. The UI may only restore().
 */
class EngineStartOwnershipScanTest {

    /** @requirement FR-033 */
    @Test
    fun engineStart_isCalledOnlyFromTheDetectionService() {
        val main = File("src/main").absoluteFile
        val callers = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.trim()
                    val isComment = code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")
                    if (!isComment && Regex("""\bengine\.start\(\)""").containsMatchIn(code)) "${file.name}:${i + 1}" else null
                }
            }
            .toList()
        assertEquals(1, callers.size, "engine.start() call sites: $callers")
        assertEquals("ParkingDetectionService.kt", callers.single().substringBefore(':'))
    }
}
