package com.adesh.devjarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGateTest {
    private fun chunk(amp: Int) = ShortArray(1600) { i -> (if (i % 2 == 0) amp else -amp).toShort() }

    @Test fun silenceIsSkipped() {
        val g = EnergyGate()
        repeat(50) { assertEquals(EnergyGate.Result.Skip, g.offer(chunk(60), 1600)) }
        assertFalse(g.isOpen)
    }

    @Test fun speechOpensWithPreRollThenClosesAfterHangover() {
        val g = EnergyGate(preRollChunks = 3, hangoverChunks = 2)
        repeat(10) { g.offer(chunk(60), 1600) }
        val first = g.offer(chunk(3000), 1600)
        assertTrue(first is EnergyGate.Result.Feed)
        assertEquals(4, (first as EnergyGate.Result.Feed).chunks.size) // 3 pre-roll + current
        assertTrue(g.isOpen)
        assertEquals(1, (g.offer(chunk(3000), 1600) as EnergyGate.Result.Feed).chunks.size)
        // Two quiet chunks still fed (hangover), then closed.
        assertTrue(g.offer(chunk(60), 1600) is EnergyGate.Result.Feed)
        assertTrue(g.offer(chunk(60), 1600) is EnergyGate.Result.Feed)
        assertEquals(EnergyGate.Result.Closed, g.offer(chunk(60), 1600))
        assertFalse(g.isOpen)
        assertEquals(EnergyGate.Result.Skip, g.offer(chunk(60), 1600))
    }

    @Test fun adaptsToNoisyRoom() {
        val g = EnergyGate()
        // Steady fan noise slightly above the minimum threshold: should be learned as floor, not trigger forever.
        var fed = 0
        repeat(200) { if (g.offer(chunk(240), 1600) !is EnergyGate.Result.Skip) fed++ }
        assertEquals(0, fed)
        assertTrue(g.offer(chunk(4000), 1600) is EnergyGate.Result.Feed)
    }
}
