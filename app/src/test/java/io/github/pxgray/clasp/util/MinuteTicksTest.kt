package io.github.pxgray.clasp.util

import io.github.pxgray.clasp.testing.FixedClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTicksTest {
    @Test
    fun `emits now, then at the next minute boundary`() = runTest {
        val clock = FixedClock(Instant.fromEpochMilliseconds(90_000)) // 00:01:30
        val ticks = mutableListOf<Instant>()
        val job = launch { minuteTicks(clock).collect { ticks += it } }
        runCurrent()
        assertEquals(listOf(Instant.fromEpochMilliseconds(90_000)), ticks)

        clock.now = Instant.fromEpochMilliseconds(120_000)
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(1, ticks.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(Instant.fromEpochMilliseconds(120_000), ticks.last())
        job.cancel()
    }
}
