package com.example.service

import android.os.SystemClock

/**
 * Monotonic clock abstraction allowing deterministic unit testing without real-time delays.
 */
fun interface ElapsedRealtimeClock {
    fun elapsedRealtime(): Long
}

object SystemElapsedRealtimeClock : ElapsedRealtimeClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
