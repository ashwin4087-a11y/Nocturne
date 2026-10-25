package com.example.engine

import com.example.data.model.ConfidenceLevel
import com.example.data.model.EtaResult
import kotlin.math.abs

object AlarmEvaluator {
    private const val RESCHEDULE_HYSTERESIS_MINUTES = 4

    data class AlarmDecision(
        val shouldTriggerNow: Boolean,
        val shouldReschedule: Boolean,
        val targetTriggerTimeMs: Long,
        val reasoning: String
    )

    /**
     * Evaluates whether the arrival threshold has been reached or if an exact alarm should be scheduled/updated.
     *
     * @param etaResult Current ETA calculation with uncertainty interval
     * @param wakeUpBufferMinutes Configured wake-up buffer (e.g. 30 minutes)
     * @param alreadyTriggered Whether the alarm has already fired for this journey
     * @param persistedTargetTriggerMs Previously persisted alarm trigger timestamp from database
     * @param currentTimeMs Current evaluation epoch millis
     */
    fun evaluate(
        etaResult: EtaResult,
        wakeUpBufferMinutes: Int,
        alreadyTriggered: Boolean,
        persistedTargetTriggerMs: Long = 0L,
        currentTimeMs: Long = System.currentTimeMillis()
    ): AlarmDecision {
        if (alreadyTriggered) {
            return AlarmDecision(
                shouldTriggerNow = false,
                shouldReschedule = false,
                targetTriggerTimeMs = 0L,
                reasoning = "Alarm has already been triggered for this journey."
            )
        }

        // Defensive threshold check incorporating uncertainty intervals:
        // Trigger immediately if:
        // 1. Point ETA is at or below the buffer (e.g. ETA <= 30 mins)
        // 2. OR under high uncertainty / degraded GPS / stationary state, lower ETA bound is at or below buffer
        //    and point ETA is within close proximity (<= buffer + 5m)
        val isAtOrBelowThreshold = etaResult.pointMinutes <= wakeUpBufferMinutes ||
                ((etaResult.confidence == ConfidenceLevel.UNRELIABLE || etaResult.confidence == ConfidenceLevel.LOW) &&
                        etaResult.lowerMinutes <= wakeUpBufferMinutes &&
                        (etaResult.pointMinutes - wakeUpBufferMinutes) <= 6)

        if (isAtOrBelowThreshold) {
            return AlarmDecision(
                shouldTriggerNow = true,
                shouldReschedule = false,
                targetTriggerTimeMs = currentTimeMs,
                reasoning = "Estimated arrival has entered the ${wakeUpBufferMinutes}-minute wake-up perimeter (${etaResult.pointMinutes}m point ETA, ${etaResult.lowerMinutes}m lower bound)."
            )
        }

        // Target trigger timestamp based on point ETA minus buffer
        val minutesUntilTrigger = etaResult.pointMinutes - wakeUpBufferMinutes
        val targetTriggerTimeMs = currentTimeMs + (minutesUntilTrigger * 60 * 1000L)

        // Hysteresis against persisted schedule: avoid re-registering AlarmManager unless shift >= 4 mins
        val timeDiffMinutes = if (persistedTargetTriggerMs > 0) {
            abs(targetTriggerTimeMs - persistedTargetTriggerMs) / (60 * 1000L)
        } else {
            999L
        }

        val shouldReschedule = timeDiffMinutes >= RESCHEDULE_HYSTERESIS_MINUTES

        return AlarmDecision(
            shouldTriggerNow = false,
            shouldReschedule = shouldReschedule,
            targetTriggerTimeMs = targetTriggerTimeMs,
            reasoning = if (shouldReschedule) {
                "Alarm scheduled for ${minutesUntilTrigger} min ahead (arrival minus ${wakeUpBufferMinutes}m buffer)."
            } else {
                "Target trigger within ${RESCHEDULE_HYSTERESIS_MINUTES}m hysteresis window; keeping durable schedule stable."
            }
        )
    }

    /**
     * Reconciles persisted alarm state on application restart or device boot.
     */
    fun reconcileOnRestart(
        persistedScheduledTimestamp: Long,
        alreadyTriggered: Boolean,
        currentTimeMs: Long = System.currentTimeMillis()
    ): AlarmDecision {
        if (alreadyTriggered) {
            return AlarmDecision(false, false, 0L, "Already triggered prior to restart.")
        }
        if (persistedScheduledTimestamp <= 0L) {
            return AlarmDecision(false, false, 0L, "No prior scheduled alarm timestamp.")
        }

        return if (currentTimeMs >= persistedScheduledTimestamp) {
            // Elapsed while app was killed! Trigger immediately
            AlarmDecision(
                shouldTriggerNow = true,
                shouldReschedule = false,
                targetTriggerTimeMs = currentTimeMs,
                reasoning = "Scheduled wake-up time elapsed while application was backgrounded/stopped."
            )
        } else {
            // Re-arm AlarmManager with remaining time
            AlarmDecision(
                shouldTriggerNow = false,
                shouldReschedule = true,
                targetTriggerTimeMs = persistedScheduledTimestamp,
                reasoning = "Re-arming pending exact alarm on application resumption."
            )
        }
    }
}
