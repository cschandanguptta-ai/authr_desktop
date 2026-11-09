package com.example

import com.example.engine.TokenBiasPolicy
import com.example.engine.ClicheCleaner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * The provider token-bias capability matrix (v3.0, plan §5.1, Table 7):
 * verified per-route decode-time bias support, the −5 suppression payload,
 * and the invariant that the deterministic linter is the compliance gate on
 * every route.
 */
class TokenBiasPolicyTest {

    private val offenderText =
        "She said breathily. He growled. She said breathily again and he growled back, very angry."

    // ------------------------------------------------------------------
    // Table 7 — the capability matrix
    // ------------------------------------------------------------------

    @Test
    fun `openai-compatible routes support decode-time bias`() {
        listOf("Groq", "NVIDIA", "Custom OpenAI", "OpenRouter").forEach { provider ->
            assertEquals(TokenBiasPolicy.Capability.LOGIT_BIAS, TokenBiasPolicy.capabilityFor(provider),
                "$provider rides logit_bias on completion APIs")
            assertEquals(TokenBiasPolicy.Strategy.BIAS_PLUS_PROMPT_PLUS_LINTER, TokenBiasPolicy.strategyFor(provider),
                "$provider gets all three enforcement layers")
        }
    }

    @Test
    fun `ollama biases via its openai-compatible surface`() {
        assertEquals(TokenBiasPolicy.Capability.LOGIT_BIAS_VIA_OPENAI_COMPAT, TokenBiasPolicy.capabilityFor("Ollama"))
        assertEquals(TokenBiasPolicy.Strategy.BIAS_PLUS_PROMPT_PLUS_LINTER, TokenBiasPolicy.strategyFor("Ollama"))
    }

    @Test
    fun `mistral and gemini run on prompt constraints plus the linter`() {
        listOf("Mistral", "Gemini").forEach { provider ->
            assertEquals(TokenBiasPolicy.Capability.NONE, TokenBiasPolicy.capabilityFor(provider),
                "$provider exposes no logit_bias parameter (verified)")
            assertEquals(TokenBiasPolicy.Strategy.PROMPT_PLUS_LINTER, TokenBiasPolicy.strategyFor(provider),
                "$provider: prompt constraints + deterministic linter; the linter is the compliance gate")
        }
        // Unknown routes fail closed to prompt+linter (never silently biased).
        assertEquals(TokenBiasPolicy.Strategy.PROMPT_PLUS_LINTER, TokenBiasPolicy.strategyFor("SomeNewProvider"))
    }

    // ------------------------------------------------------------------
    // The bias payload
    // ------------------------------------------------------------------

    @Test
    fun `bias payload maps offender tokens to minus five`() {
        val payload = TokenBiasPolicy.biasPayload("Groq", offenderText)
        assertTrue(payload.isNotEmpty(), "bias-capable routes get a payload")
        assertTrue(payload.values.all { it == TokenBiasPolicy.BIAS_WEIGHT },
            "every offender token is suppressed at −5: $payload")
        assertTrue(payload.containsKey("breathily") && payload.containsKey("growled"),
            "the frequent offender tokens are in the payload: $payload")
        assertTrue(payload.size <= TokenBiasPolicy.MAX_BIASED_TOKENS, "payload respects the route ceiling")
    }

    @Test
    fun `non-bias routes never emit a payload`() {
        assertEquals(emptyMap(), TokenBiasPolicy.biasPayload("Gemini", offenderText),
            "Gemini: prompt + linter only (no logit_bias parameter)")
        assertEquals(emptyMap(), TokenBiasPolicy.biasPayload("Mistral", offenderText),
            "Mistral: prompt + linter only")
    }

    @Test
    fun `clean text emits an empty payload even on bias routes`() {
        val clean = "The harbor bell counted the hour while Tanis roped the crate to the dock."
        assertEquals(emptyMap(), TokenBiasPolicy.biasPayload("Groq", clean),
            "no offenders, no bias — decode time stays untouched")
    }

    // ------------------------------------------------------------------
    // The enforcement plan
    // ------------------------------------------------------------------

    @Test
    fun `enforcement plan carries constraints bias and the linter gate`() {
        val groqPlan = TokenBiasPolicy.enforcementPlan("Groq", offenderText)
        assertTrue(groqPlan.promptConstraints.contains("FORBIDDEN"), "the prompt block rides every plan")
        assertTrue(groqPlan.logitBias.isNotEmpty(), "bias payload present on Groq")
        assertTrue(groqPlan.linterIsComplianceGate, "the linter decides what ships")

        val geminiPlan = TokenBiasPolicy.enforcementPlan("Gemini", offenderText)
        assertTrue(geminiPlan.promptConstraints.contains("FORBIDDEN"), "prompt constraints on Gemini too")
        assertFalse(geminiPlan.logitBias.isNotEmpty(), "no bias on Gemini")
        assertTrue(geminiPlan.linterIsComplianceGate, "the linter is the compliance gate on Gemini")
    }

    @Test
    fun `offender tokens are deterministic and ranked`() {
        val t1 = ClicheCleaner.offenderTokens(offenderText)
        val t2 = ClicheCleaner.offenderTokens(offenderText)
        assertEquals(t1, t2, "frequency ranking is deterministic")
        assertTrue(t1.first().let { it == "breathily" || it == "growled" },
            "the most frequent offenders rank first: $t1")
    }
}
