package com.example.engine.voice

import com.example.data.model.VoiceDriftLog
import kotlin.math.abs

/**
 * VOICE DRIFT TRACKER (v3.0 port S0/S1, spec ADR-0013 = desktop ADR-0019):
 * per-feature drift against a baseline feature vector, weighted composite
 * drift, and the cumulative exponential moving average (alpha = 0.1) that
 * catches slow voice collapse across a book (spec Invariant 15).
 *
 * The math follows spec engine-enhancements §2 exactly:
 *   drift(f) = |current - baseline| / max(baseline, epsilon)
 *   composite = Σ weight(f) * drift(f) / Σ weight(f)
 *   cumulative_k = α·composite_k + (1-α)·cumulative_{k-1}
 *   flagged = (composite > 0.30 OR cumulative > 0.20) AND NOT evolution-applied
 *
 * Voice evolution (author-declared intentional change) suppresses the
 * alert but is still RECORDED — the writer sees the movement, the gate
 * stays quiet (writer sovereignty, ADR-0001).
 */
object VoiceDriftTracker {

    /** Spec §2.1 weights: the load-bearing voice signature features. */
    val FEATURE_WEIGHTS: Map<String, Double> = linkedMapOf(
        "sentence_len_mean" to 1.5,
        "sentence_len_sd" to 1.5,      // burstiness — the spec's headline metric
        "ttr_windowed" to 1.0,
        "avg_word_len" to 0.75,
        "contraction_ratio" to 1.0,
        "dialogue_ratio" to 1.0,
        "filter_word_density" to 0.5,
        "adverb_per1k" to 0.5,
        "passive_per100" to 0.5,
        "sentence_opener_variety" to 0.5,
        "figurative_per1k" to 0.5
    )

    /** Guard against divide-by-near-zero baselines (e.g. features that are 0). */
    private const val BASELINE_EPSILON = 0.5

    data class DriftResult(
        /** Per-feature drift 0.0..1.0 (clamped): |cur - base| / max(base, eps). */
        val perFeatureDrift: Map<String, Double>,
        /** Weighted composite drift 0.0..1.0. */
        val compositeDrift: Double,
        /** Running EMA including this chapter. */
        val cumulativeDrift: Double,
        /** Spec flag rule, evolution-aware. */
        val flagged: Boolean,
        /** True when an active voice evolution covered this chapter. */
        val evolutionApplied: Boolean
    )

    /**
     * Compute chapter drift. [baseline] is the voice profile target (or a
     * rolling baseline from earlier confirmed chapters); [previousCumulative]
     * seeds the EMA (0.0 for the first chapter).
     */
    fun computeDrift(
        current: Map<String, Double>,
        baseline: Map<String, Double>,
        previousCumulative: Double = 0.0,
        alpha: Double = VoiceDriftLog.DEFAULT_EMA_ALPHA,
        evolutionActive: Boolean = false
    ): DriftResult {
        val perFeature = LinkedHashMap<String, Double>()
        var weightedSum = 0.0
        var weightTotal = 0.0
        for ((feature, weight) in FEATURE_WEIGHTS) {
            val cur = current[feature] ?: continue
            val base = baseline[feature] ?: continue
            if (cur < 0 || base < 0) continue // UNDEFINED sentinel — skip
            val drift = (abs(cur - base) / maxOf(base, BASELINE_EPSILON)).coerceIn(0.0, 1.0)
            perFeature[feature] = round4(drift)
            weightedSum += weight * drift
            weightTotal += weight
        }
        val composite = if (weightTotal == 0.0) 0.0 else round4(weightedSum / weightTotal)
        val cumulative = round4(alpha * composite + (1 - alpha) * previousCumulative)
        val rawFlag = composite > VoiceDriftLog.COMPOSITE_FLAG ||
            cumulative > VoiceDriftLog.CUMULATIVE_FLAG
        return DriftResult(
            perFeatureDrift = perFeature,
            compositeDrift = composite,
            cumulativeDrift = cumulative,
            flagged = rawFlag && !evolutionActive,
            evolutionApplied = evolutionActive && rawFlag
        )
    }

    /**
     * Rolling baseline from earlier chapters (spec: baseline = voice
     * profile when present, else the mean of confirmed history).
     */
    fun rollingBaseline(history: List<Map<String, Double>>): Map<String, Double> {
        if (history.isEmpty()) return emptyMap()
        val baseline = LinkedHashMap<String, Double>()
        for (feature in FEATURE_WEIGHTS.keys) {
            val values = history.mapNotNull { it[feature] }.filter { it >= 0 }
            if (values.isNotEmpty()) baseline[feature] = values.average()
        }
        return baseline
    }

    private fun round4(v: Double): Double = Math.round(v * 10000.0) / 10000.0
}
