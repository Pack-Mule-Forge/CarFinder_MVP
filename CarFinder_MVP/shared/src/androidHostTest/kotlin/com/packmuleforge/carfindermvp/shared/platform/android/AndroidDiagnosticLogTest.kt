package com.packmuleforge.carfindermvp.shared.platform.android

import com.packmuleforge.carfindermvp.shared.platform.DiagnosticEvent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** @requirement QR-003 */
@RunWith(RobolectricTestRunner::class)
class AndroidDiagnosticLogTest {

    @BeforeTest
    fun clearLog() {
        ShadowLog.clear()
    }

    /** @requirement FR-020 */
    @Test
    fun eachEventTypeProducesOneLogLineWithNoCoordinates() {
        val events = listOf(DiagnosticEvent.StoreUnreadable, DiagnosticEvent.RecordNormalized, DiagnosticEvent.ReadingDropped)
        val log = AndroidDiagnosticLog()
        events.forEach(log::record)
        val lines = ShadowLog.getLogsForTag("CarFinder")
        assertEquals(events.size, lines.size)
        events.zip(lines).forEach { (event, line) -> assertTrue(line.msg.contains(event::class.simpleName!!)) }
        assertTrue(lines.none { DECIMAL_COORDINATE.containsMatchIn(it.msg) }, lines.joinToString { it.msg })
    }

    private companion object {
        val DECIMAL_COORDINATE = Regex("""-?\d{1,3}\.\d{3,}""")
    }
}
