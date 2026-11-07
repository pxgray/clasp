package io.github.pxgray.clasp.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/** The time now, then at every minute boundary, for as long as it is collected (only while a screen shows). */
fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        delay(60_000L - Math.floorMod(now.toEpochMilliseconds(), 60_000L))
    }
}
