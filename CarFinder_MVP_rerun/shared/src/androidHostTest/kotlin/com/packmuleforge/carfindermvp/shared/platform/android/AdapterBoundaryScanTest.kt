package com.packmuleforge.carfindermvp.shared.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @requirement QR-003
 */
class AdapterBoundaryScanTest {

    private val commonSource: String by lazy {
        SourceTree.kotlinFiles("shared/src/commonMain").joinToString("\n") { it.readText() }
    }

    /** @requirement QR-003 */
    @Test
    fun onlyThePlatformFactoryIsExpectActual() {
        val expects = EXPECT_DECLARATION.findAll(commonSource).map { it.groupValues[1].trim() }.toList()
        val unexpected = expects.filterNot { it in ALLOWED_EXPECTS }
        assertTrue(unexpected.isEmpty(), "Unexpected expect declarations: $unexpected")
    }

    /** @requirement QR-003 */
    @Test
    fun everyAdapterIsAnInterface() {
        for (name in ADAPTERS) {
            assertTrue(Regex("""\binterface\s+$name\b""").containsMatchIn(commonSource), "$name must be an interface")
        }
    }

    /** @requirement QR-003 */
    @Test
    fun permissionControllerHasExactlyOneSuspendFunction() {
        val start = Regex("""\binterface\s+PermissionController\b[^{]*\{""").find(commonSource)
        assertTrue(start != null, "PermissionController not found")
        val body = bracedBody(commonSource, start.range.last)
        assertEquals(1, Regex("""\bsuspend\s+fun\b""").findAll(body).count())
    }

    private fun bracedBody(text: String, openBraceIndex: Int): String {
        var depth = 0
        for (i in openBraceIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(openBraceIndex + 1, i)
            }
        }
        error("Unbalanced braces")
    }

    private companion object {
        val EXPECT_DECLARATION = Regex("""\bexpect\s+((?:class|fun|object|val|interface)\s+\w+)""")
        val ALLOWED_EXPECTS = setOf("class PlatformContext", "fun createPlatformAdapters")
        val ADAPTERS = listOf(
            "LocationSource", "HeadingSource", "ActivitySignalSource", "ParkingStore", "PermissionController",
            "MonotonicClock", "WallClock", "DiagnosticLog",
        )
    }
}
