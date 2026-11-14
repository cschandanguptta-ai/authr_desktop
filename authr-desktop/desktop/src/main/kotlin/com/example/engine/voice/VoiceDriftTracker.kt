package com.example.engine.voice

import com.example.data.model.VoiceDriftLog
import kotlin.math.abs
import kotlin.math.min

/**
 * VOICE DRIFT TRACKER (v3.0 port S1, spec ADR-0013 = desktop ADR-0020):
 * per-feature tolerance-band drift against a target feature vector,
 * genre-weighted composite, and the cumulative exponential moving average
 * (alpha = 0.1) that catches slow voice collapse across a book (spec
 * Invariant 15).
 *
 * The math follows spec engine-enhancements §1.3 exactly:
 *   target    = profile, OVERRIDDEN by active evolution entries (the
 *               author said these features SHOULD move — drift is then
 *               measured against the shifted target, not suppressed blind)
 *   drift(f)  = 0.0 if low <= measured <= high  (tolerance band, default ±20%)
 *               else min(|m-low|, |m-high|) / max(high-low, 0.01), clamped 1.0
 *   composite = Σ weight(f)·drift(f) / Σ weight(f)   (genre weights)
 *   cumulative_k = α·composite_k + (1-α)·cumulative_{k-1}
 *   flagged   = (composite > 0.30 OR cumulative > 0.20) AND NOT evolution_applied
 *
 * Wide initial tolerance bands (spec calibration advice: start ±20-30%,
 * narrow only after observing false-positive rates) mean the composite is
 * a WARNING axis in the engagement analytics — it never auto-blocks a
 * stage by itself (writer sovereignty, ADR-0001).
 */
object VoiceDriftTracker {

    /** Spec §1.3 default tolerance: ±20% around the target. */
    const val DEFAULT_TOLERANCE_LOW = 0.8
    const val DEFAULT_TOLERANCE_HIGH = 1.2

    /**
     * Spec genre weight table (GENRE_FEATURE_WEIGHTS), restricted to the
     * deterministic extractor's feature names. POV/tense consistency,
     * show/tell and clause depth are LLM-assisted features (S2+) and are
     * deliberately absent — the composite only weights what is measured.
     */
    val GENRE_WEIGHTS: Map<String, Map<String, Double>> = mapOf(
        "literary" to mapOf(
            "sentence_len_sd" to 1.5, "figurative_per1k" to 2.0, "dialogue_ratio" to 0.8,
            "contraction_ratio" to 1.0, "passive_per100" to 1.5,
            "sentence_opener_variety" to 1.5
        ),
        "thriller" to mapOf(
            "sentence_len_sd" to 1.0, "figurative_per1k" to 0.5, "dialogue_ratio" to 1.5,
            "contraction_ratio" to 1.2, "passive_per100" to 1.0,
            "sentence_opener_variety" to 1.0
        ),
        "romance" to mapOf(
            "sentence_len_sd" to 0.8, "figurative_per1k" to 1.0, "dialogue_ratio" to 1.8,
            "contraction_ratio" to 1.5, "passive_per100" to 0.5,
            "sentence_opener_variety" to 1.0
        ),
        "nonfiction" to mapOf(
            "sentence_len_sd" to 1.0, "figurative_per1k" to 0.3, "dialogue_ratio" to 0.2,
            "contraction_ratio" to 0.8, "passive_per100" to 1.5,
            "sentence_opener_variety" to 0.8
        )
    )

    /** Default weights when the genre has no entry: all measured features. */
    val DEFAULT_WEIGHTS: Map<String, Double> = mapOf(
        "sentence_len_mean" to 1.0, "sentence_len_sd" to 1.0, "ttr_windowed" to 1.0,
        "avg_word_len" to 0.75, "contraction_ratio" to 1.0, "dialogue_ratio" to 1.0,
        "filter_word_density" to 0.5, "adverb_per1k" to 0.5, "passive_per100" to 0.5,
        "sentence_opener_variety" to 0.5, "figurative_per1k" to 0.5
    )

    /** Genre key resolution from the Book.genre free-text field. */
    fun genreKey(genre: String): String {
        val g = genre.lowercase()
        return when {
            "literary" in g -> "literary"
            "thrill" in g || "suspense" in g || "mystery" in g || "crime" in g -> "thriller"
            "romance" in g || "romantic" in g -> "romance"
            "non-fiction" in g || "nonfiction" in g || "history" in g || "science" in g ||
                "memoir" in g || "how-to" in g || "guide" in g -> "nonfiction"
            else -> "default"
        }
    }

    data class DriftResult(
        /** Per-feature drift 0.0..1.0: 0 inside the tolerance band. */
        val perFeatureDrift: Map<String, Double>,
        /** Weighted composite drift 0.0..1.0. */
        val compositeDrift: Double,
        /** Running EMA including this chapter. */
        val cumulativeDrift: Double,
        /** Spec flag rule, evolution-aware. */
        val flagged: Boolean,
        /** True when an active voice evolution covered this chapter. */
        val evolutionApplied: Boolean,
        /** The three worst features (spec: worst_features for the UI). */
        val worstFeatures: List<Pair<String, Double>>
    )

    /**
     * Compute chapter drift per spec §1.3. [targetFeatures] is the voice
     * profile target (persona DNA or rolling baseline); [evolutionOverrides]
     * are the adjusted targets of an ACTIVE evolution entry — they REPLACE
     * the corresponding base targets before drift is computed (spec:
     * `apply_evolution_overrides`). [previousCumulative] seeds the EMA.
     */
    fun computeDrift(
        measured: Map<String, Double>,
        targetFeatures: Map<String, Double>,
        previousCumulative: Double = 0.0,
        alpha: Double = VoiceDriftLog.DEFAULT_EMA_ALPHA,
        weights: Map<String, Double> = DEFAULT_WEIGHTS,
        toleranceLow: Double = DEFAULT_TOLERANCE_LOW,
        toleranceHigh: Double = DEFAULT_TOLERANCE_HIGH,
        evolutionOverrides: Map<String, Double> = emptyMap()
    ): DriftResult {
        // 1. Target = base profile, overridden by the active evolution.
        val target = HashMap(targetFeatures)
        val evolutionApplied = evolutionOverrides.isNotEmpty()
        evolutionOverrides.forEach { (k, v) -> target[k] = v }

        // 2. Tolerance-band drift per feature (0.0 inside the band).
        val perFeature = LinkedHashMap<String, Double>()
        for ((feature, targetVal) in target) {
            if (feature !in weights) continue // only weight what is measured
            val measuredVal = measured[feature] ?: continue
            if (measuredVal < 0 || targetVal < 0) continue // UNDEFINED sentinel
            val low = targetVal * toleranceLow
            val high = targetVal * toleranceHigh
            val drift = if (measuredVal in low..high) {
                0.0
            } else {
                val distance = min(abs(measuredVal - low), abs(measuredVal - high))
                val rangeSize = (high - low).coerceAtLeast(0.01)
                (distance / rangeSize).coerceIn(0.0, 1.0)
            }
            perFeature[feature] = round4(drift)
        }

        // 3. Weighted composite over the measured features.
        var weightedSum = 0.0
        var weightTotal = 0.0
        for ((feature, drift) in perFeature) {
            val w = weights.getValue(feature)
            weightedSum += w * drift
            weightTotal += w
        }
        val composite = if (weightTotal == 0.0) 0.0 else round4(weightedSum / weightTotal)

        // 4. Cumulative EMA.
        val cumulative = round4(alpha * composite + (1 - alpha) * previousCumulative)

        // 5. Flagging (spec: evolution suppresses the alert, not the measurement).
        val rawFlag = composite > VoiceDriftLog.COMPOSITE_FLAG ||
            cumulative > VoiceDriftLog.CUMULATIVE_FLAG

        // worst_features: top 3 by drift (spec UI surface).
        val worst = perFeature.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(3)
            .map { it.key to it.value }

        return DriftResult(
            perFeatureDrift = perFeature,
            compositeDrift = composite,
            cumulativeDrift = cumulative,
            flagged = rawFlag && !evolutionApplied,
            evolutionApplied = evolutionApplied && rawFlag,
            worstFeatures = worst
        )
    }

    /**
     * Rolling baseline from earlier chapters (spec: baseline = voice
     * profile when present, else the mean of confirmed history).
     */
    fun rollingBaseline(history: List<Map<String, Double>>): Map<String, Double> {
        if (history.isEmpty()) return emptyMap()
        val baseline = LinkedHashMap<String, Double>()
        for (feature in DEFAULT_WEIGHTS.keys) {
            val values = history.mapNotNull { it[feature] }.filter { it >= 0 }
            if (values.isNotEmpty()) baseline[feature] = values.average()
        }
        return baseline
    }

    private fun round4(v: Double): Double = Math.round(v * 10000.0) / 10000.0
}
