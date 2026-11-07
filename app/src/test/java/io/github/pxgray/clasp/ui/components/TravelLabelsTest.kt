package io.github.pxgray.clasp.ui.components

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pxgray.clasp.domain.TravelStage
import io.github.pxgray.clasp.domain.TravelStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class TravelLabelsTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val departs = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun status(stage: TravelStage, daysAway: Int = 0, timed: Boolean = true, gate: String? = null) =
        TravelStatus(stage, departs.takeIf { timed }, daysAway, isActive = true, gate = gate)

    private fun label(s: TravelStatus, before: Duration, short: Boolean = false) = s.label(res, departs - before, short)

    @Test
    fun `stage labels`() {
        assertEquals("Departed", label(status(TravelStage.DEPARTED), (-5).minutes))
        assertEquals("Boarding · Gate B12", label(status(TravelStage.BOARDING, gate = "B12"), 10.minutes))
        assertEquals("Boarding now", label(status(TravelStage.BOARDING), 10.minutes))
    }

    @Test
    fun `leaving soon counts down, rounding up to the minute`() {
        val s = status(TravelStage.LEAVING_SOON)
        assertEquals("Departs in 3 h", label(s, 3.hours))
        assertEquals("Departs in 2 h 10 m", label(s, 2.hours + 10.minutes))
        assertEquals("Departs in 2 h 10 m", label(s, 2.hours + 9.minutes + 30.seconds))
        assertEquals("Departs in 45 m", label(s, 45.minutes))
        assertEquals("Departs in 1 m", label(s, 1.seconds))
        assertEquals("2 h 10 m", label(s, 2.hours + 10.minutes, short = true))
        assertEquals("45 m", label(s, 45.minutes, short = true))
    }

    @Test
    fun `upcoming wording by day`() {
        assertEquals("In 12 days", label(status(TravelStage.UPCOMING, daysAway = 12), 300.hours))
        assertEquals("Tomorrow", label(status(TravelStage.UPCOMING, daysAway = 1), 20.hours))
        assertEquals("Departs in 6 h", label(status(TravelStage.UPCOMING), 6.hours + 30.minutes))
        assertEquals("6 h", label(status(TravelStage.UPCOMING), 6.hours + 30.minutes, short = true))
        assertEquals("Today", label(status(TravelStage.UPCOMING, timed = false), Duration.ZERO))
    }
}
