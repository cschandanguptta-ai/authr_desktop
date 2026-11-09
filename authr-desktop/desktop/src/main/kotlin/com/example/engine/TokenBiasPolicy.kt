package com.example.engine

/**
 * Provider token-bias capability matrix (v3.0, plan §5.1, Table 7).
 *
 * Token-bias capability differs by provider, and the cleaner adapts per
 * route. The load-bearing fact from the Rev 1 spike: Gemini exposes no
 * logit_bias parameter, so for Gemini routes the prompt constraint plus the
 * deterministic linter are the only enforcement layers — acceptable because
 * the linter, not the bias, is the gate that decides what ships.
 */
object TokenBiasPolicy {

    /** Decode-time bias support per provider route (plan Table 7, col 2). */
    enum class Capability {
        /** logit_bias on completion APIs (OpenAI-compatible family). */
        LOGIT_BIAS,
        /** Bias only via the provider's OpenAI-compatible surface. */
        LOGIT_BIAS_VIA_OPENAI_COMPAT,
        /** No logit_bias in the provider's API (verified). */
        NONE
    }

    /** The v3.0 enforcement strategy per route (plan Table 7, col 3). */
    enum class Strategy {
        /** Bias payload + prompt constraints + linter (all three layers). */
        BIAS_PLUS_PROMPT_PLUS_LINTER,
        /** Prompt constraints + deterministic linter (the linter is the gate). */
        PROMPT_PLUS_LINTER
    }

    /** OpenAI-compatible family: Groq, NVIDIA, Custom OpenAI endpoints. */
    private val openAiCompatibleRoutes = setOf("Groq", "NVIDIA", "Custom OpenAI")

    /**
     * Plan Table 7 — the verified capability matrix. OpenRouter shares the
     * OpenAI-compatible request shape (same enforcement as that family).
     */
    fun capabilityFor(provider: String): Capability = when (provider) {
        in openAiCompatibleRoutes, "OpenRouter" -> Capability.LOGIT_BIAS
        "Ollama" -> Capability.LOGIT_BIAS_VIA_OPENAI_COMPAT
        "Mistral", "Gemini" -> Capability.NONE
        else -> Capability.NONE
    }

    /** The enforcement strategy the cleaner runs for this route. */
    fun strategyFor(provider: String): Strategy = when (capabilityFor(provider)) {
        Capability.LOGIT_BIAS, Capability.LOGIT_BIAS_VIA_OPENAI_COMPAT ->
            Strategy.BIAS_PLUS_PROMPT_PLUS_LINTER
        else -> Strategy.PROMPT_PLUS_LINTER
    }

    /** The decode-time bias weight from plan Table 7. */
    const val BIAS_WEIGHT = -5

    /** Hard ceiling on biasable tokens per request (OpenAI-compatible limit: 300). */
    const val MAX_BIASED_TOKENS = 300

    /**
     * Builds the logit_bias payload for a route: the most frequent offender
     * tokens in [text] (from the cliché matrix's matches) mapped to
     * [BIAS_WEIGHT]. The payload is emitted only for bias-capable routes;
     * on Gemini/Mistral routes this returns an empty map and the prompt
     * constraints + linter carry enforcement alone.
     *
     * The payload keys are the offender surface forms; the transport layer
     * owns the token-ID encoding against the routed model's tokenizer and
     * attaches the map only where it can encode it (P2 report, integration
     * note). Either way the deterministic linter remains the compliance gate.
     */
    fun biasPayload(provider: String, text: String, limit: Int = MAX_BIASED_TOKENS): Map<String, Int> {
        if (strategyFor(provider) != Strategy.BIAS_PLUS_PROMPT_PLUS_LINTER) return emptyMap()
        return ClicheCleaner.offenderTokens(text, limit).associateWith { BIAS_WEIGHT }
    }

    /**
     * The complete enforcement plan for one L4 generation on [provider]:
     * the prompt constraint block (all routes) plus the decode-time bias
     * payload (bias-capable routes) — with the linter always last.
     */
    data class EnforcementPlan(
        val provider: String,
        val strategy: Strategy,
        /** The hard-constraint block injected into the drafting prompt. */
        val promptConstraints: String,
        /** The decode-time logit_bias payload (empty on non-bias routes). */
        val logitBias: Map<String, Int>,
        /** True on every route — the linter decides what ships. */
        val linterIsComplianceGate: Boolean = true
    )

    fun enforcementPlan(provider: String, text: String): EnforcementPlan = EnforcementPlan(
        provider = provider,
        strategy = strategyFor(provider),
        promptConstraints = ClicheCleaner.promptConstraints(),
        logitBias = biasPayload(provider, text)
    )
}
