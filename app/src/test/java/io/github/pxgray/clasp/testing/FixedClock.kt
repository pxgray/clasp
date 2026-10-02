package io.github.pxgray.clasp.testing

import kotlin.time.Clock
import kotlin.time.Instant

class FixedClock(var now: Instant = Instant.fromEpochMilliseconds(1_000_000)) : Clock {
    override fun now(): Instant = now
}
