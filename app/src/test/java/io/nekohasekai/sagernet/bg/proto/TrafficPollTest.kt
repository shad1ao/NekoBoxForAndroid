package io.nekohasekai.sagernet.bg.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficPollTest {
    @Test
    fun trafficPollingUsesTheOneSecondDefaultWithoutClashApi() {
        assertEquals(1000, DEFAULT_SPEED_INTERVAL_MS)
        assertTrue(shouldPollTraffic(DEFAULT_SPEED_INTERVAL_MS))
        assertTrue(shouldPollTraffic(500))
    }

    @Test
    fun explicitDisableStopsPolling() {
        assertFalse(shouldPollTraffic(0))
        assertFalse(shouldPollTraffic(-1))
    }
}
