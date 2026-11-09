# ADR-0001: Multi-Agent LLM Orchestration with Priority Queue & Dynamic Fallback

## Status
Accepted

## Context
Writing complete novels requires hundreds of prompt calls with varying latency and strict token rate limits (429 errors). Different tasks (plotting, dialogue, world lore) benefit from specialized system prompts, while reliance on a single LLM provider risks halting long generation runs when rate limits or outages occur.

## Decision
Implement a central `LlmOrchestrator` (`app/src/main/java/com/example/data/api/LlmOrchestrator.kt`) backed by a priority-aware `RequestQueueManager` (`app/src/main/java/com/example/data/api/RequestQueueManager.kt`).
1. Divide tasks into 6 domain agents: `EXECUTIVE`, `PLOT`, `CHARACTER`, `WORLD`, `SCENE`, `CONTINUITY`.
2. Allow assigning custom LLM providers per agent.
3. Automatically execute a cascading provider fallback sequence: `[Preferred, Groq, NVIDIA, Gemini, Mistral, OpenRouter, Ollama, Custom OpenAI]`.
4. Throttle calls via `Semaphore(concurrencyLimit = 2)` and sliding-window rate limiter (`15 RPM`).

## Alternatives (Evidenced in Code)
- *Direct Provider Service Calls*: Code previously made direct REST calls to `geminiService` or `mistralService`. Discarded because provider failures abruptly halted background Workers without retry.

## Consequences
- **Positive**: High resilience to 429 rate limits; automated failover to alternate free or paid providers.
- **Negative**: Increased latency when walking multiple exhausted providers; complex in-memory state tracking.

## Do Not Change Unless
- The application moves to a dedicated server-side proxy gateway that handles failover, authentication, and queue throttling outside of the Android client process.

## Evidence
- `app/src/main/java/com/example/data/api/LlmOrchestrator.kt:11-28, 106-120` [FACT]
- `app/src/main/java/com/example/data/api/RequestQueueManager.kt:22-105` [FACT]
