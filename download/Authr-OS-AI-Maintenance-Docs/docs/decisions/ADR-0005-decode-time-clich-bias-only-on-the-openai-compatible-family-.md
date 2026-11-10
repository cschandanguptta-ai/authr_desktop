# ADR-0005 — Decode-time cliché bias only on the OpenAI-compatible family; serialization guards optional

**Status:** Accepted (P2)
**Context:** The plan's Table 7 wants token-bias suppression where a provider supports it; Gemini/Mistral/OpenRouter/Ollama lack `logit_bias`.
**Decision:** `LlmOrchestrator.callAgent` takes an optional `logitBias: Map<String, Int>?`; `OpenAiChatRequest.logit_bias` is serialized **only when non-null**, so bias-incapable routes and the Moshi adapter are untouched. Prompt constraints + the deterministic linter remain the enforcement everywhere (risk register: Gemini routes lack decode-time bias — accepted).
**Alternatives considered:** vendor-specific request forks (rejected — adapter churn); dropping decode-time bias (rejected — plan Table 7).
**Consequences:** `TokenBiasPolicyTest` pins payload construction (−5 weight, 300-entry ceiling); a broken payload surfaces only at runtime as provider 400s (no network tests — see testing.md).
**Do not change unless:** a provider gains/loses bias support or the plan changes the weight/ceiling.
**Evidence:** `data/api/LlmOrchestrator.kt` (logitBias param + provider blocks), `engine/TokenBiasPolicy.kt`, `TokenBiasPolicyTest`.
