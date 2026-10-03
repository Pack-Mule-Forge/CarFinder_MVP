package com.packmuleforge.carfinder.shared.fake

import com.packmuleforge.carfinder.shared.platform.HeadingProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test fake for HeadingProvider. Yields headings from a pre-scripted list in sequence.
 * Supports testing heading edge cases like wrapping (359° → 1°).
 */
class FakeHeadingProvider(
    private val headings: List<Double> = emptyList()
) : HeadingProvider {
    override fun headingDegrees(): Flow<Double> = flow {
        for (heading in headings) {
            emit(heading % 360.0)  // Normalize to [0, 360)
        }
    }
}
