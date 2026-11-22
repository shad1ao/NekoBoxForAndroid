package io.nekohasekai.sagernet.bg.proto

/** Matches the settings menu default of 1s. A stored 0 means the user chose Disable. */
internal const val DEFAULT_SPEED_INTERVAL_MS = 1000

/**
 * Speed and per-profile traffic are read from the core stats tracker.
 * That tracker is independent of the Clash API / YACD dashboard.
 */
internal fun shouldPollTraffic(speedIntervalMs: Int): Boolean = speedIntervalMs > 0
