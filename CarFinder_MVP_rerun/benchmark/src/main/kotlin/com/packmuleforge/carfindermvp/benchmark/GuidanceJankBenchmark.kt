package com.packmuleforge.carfindermvp.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FR-027 / SC-008: measures frame timing during a 60 s guidance session with heading updates at the UI sensor
 * rate, on the reference test device named in the spec's Assumptions. Macrobenchmark writes the results to its
 * JSON report; `tools/benchmark/Assert-FrameBudget.ps1` then fails if the p95 `frameDurationCpuMs` exceeds the
 * 16.7 ms budget.
 *
 * @requirement FR-027
 */
@RunWith(AndroidJUnit4::class)
class GuidanceJankBenchmark {

    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Test
    fun guidanceSession() = benchmark.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 1,
        startupMode = StartupMode.COLD,
    ) {
        startActivityAndWait()
        // The benchmark build's synthetic feeds keep the cone rotating and the distance changing.
        Thread.sleep(SESSION_MILLIS)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.packmuleforge.carfindermvp"
        const val SESSION_MILLIS = 60_000L
    }
}
