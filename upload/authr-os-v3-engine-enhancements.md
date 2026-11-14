# AUTHR OS v3.0 — Engine Enhancement Specifications

*Detailed specs for Voice Engine, Context Orchestrator, and Quality Gate extensions*

---

## 1. Voice Engine Enhancements (`retention_and_editorial.py`)

### 1.1 Current State (v2.1)

v2.1 already provides:
- 37 Style Personas with 4-Layer Voice DNA
- Per-character `speechFingerprint` (max_words_per_turn, contraction_ratio, register, verbal_tics, forbidden_lexicon)
- Sentence Burstiness ($\sigma_S \ge 4.2$)
- Filter-Word Density (< 3.0 per 1,000 words)
- Per-chapter voice drift detection ($\Delta W_c > 6.0$)

### 1.2 v3 Enhancement 1: Expanded Feature Extraction (20+ Metrics)

Extend the existing telemetry from 3 metrics to 20+ features computed per beat/chapter:

#### Structural Features (computed via spaCy, no LLM needed)

| Feature | Computation | Threshold/Range |
|:---|:---|:---|
| `sentence_len_mean` | Mean word count per sentence | Genre-specific (8–22) |
| `sentence_len_sd` | Standard deviation of sentence lengths | ≥ 4.2 (existing burstiness) |
| `paragraph_len_mean` | Mean sentence count per paragraph | Genre-specific |
| `clause_depth_avg` | Average subordinate clause nesting via dependency parse | 1.2–3.5 |
| `sentence_opener_variety` | Shannon entropy of first-word POS tags | ≥ 2.0 bits |
| `consecutive_same_opener` | Max consecutive sentences starting with same word/POS | ≤ 2 |
| `punct_rhythm` | Frequency map of em-dashes, semicolons, ellipses per 1k words | Profile-matched |
| `contraction_freq` | `don't`/`do not` ratio | Profile-matched |

#### Lexical Features

| Feature | Computation | Threshold/Range |
|:---|:---|:---|
| `ttr_windowed` | Type-token ratio (1,000-word sliding window) | 0.30–0.55 |
| `hapax_ratio` | Words appearing exactly once / total unique words | Profile-matched |
| `avg_word_len` | Mean character count per word | 4.0–6.5 |
| `figurative_density` | Simile/metaphor/personification count per 1k words | Genre-specific |
| `sensory_by_sense` | Frequency per sense (sight, sound, touch, taste, smell) per 1k words | Sum ≥ 3.0 |
| `adverb_freq` | `-ly` adverbs per 1k words | ≤ 8.0 |
| `passive_voice_freq` | Passive constructions per 100 sentences | ≤ 15% |
| `filter_word_density` | Class-2 filter words per 1k words (existing) | < 3.0 |

#### Narrative Features (LLM-classified on sample, cached)

| Feature | Computation | Gate |
|:---|:---|:---|
| `pov_consistency` | Classification: 1st, 3rd limited, 3rd omni, 2nd, deep | 0 violations per scene |
| `tense_consistency` | Past/present tense per sentence | 0 violations per scene |
| `narrative_distance` | Close (deep POV) vs. distant — classified per paragraph | Profile-matched |
| `show_tell_ratio` | Showing vs. telling classification per passage | Genre-specific |
| `dialogue_ratio` | % of text inside quotation marks per chapter | Genre-specific |

#### Feature Extraction Implementation

```python
def extract_full_features(text: str, nlp) -> dict:
    """
    Extract 20+ features from generated text.
    Structural + lexical features use spaCy (fast, deterministic).
    Narrative features use cached LLM classification (expensive, batched).
    """
    doc = nlp(text)
    sentences = list(doc.sents)
    
    # Structural
    sent_lens = [len(list(s)) for s in sentences]  # word counts
    features = {
        "sentence_len_mean": statistics.mean(sent_lens),
        "sentence_len_sd": statistics.stdev(sent_lens) if len(sent_lens) > 1 else 0,
        "paragraph_len_mean": compute_paragraph_lengths(text),
        "clause_depth_avg": mean_clause_depth(doc),
        "sentence_opener_variety": opener_entropy(sentences),
        "consecutive_same_opener": max_consecutive_openers(sentences),
        "punct_rhythm": punct_frequency_map(text),
        "contraction_freq": contraction_ratio(text),
    }
    
    # Lexical
    features.update({
        "ttr_windowed": windowed_ttr(doc, window=1000),
        "hapax_ratio": hapax_legomena_ratio(doc),
        "avg_word_len": mean_word_length(doc),
        "figurative_density": figurative_language_count(doc) / max(len(doc) / 1000, 0.1),
        "sensory_by_sense": sensory_word_frequency(doc),
        "adverb_freq": ly_adverb_count(doc) / max(len(doc) / 1000, 0.1),
        "passive_voice_freq": passive_ratio(doc),
        "filter_word_density": filter_word_count(text) / max(len(doc) / 1000, 0.1),
    })
    
    # Dialogue
    features["dialogue_ratio"] = dialogue_percentage(text)
    
    return features
```

### 1.3 v3 Enhancement 2: Cumulative Voice Drift Tracking (ADR-0013)

#### Problem

v2.1 checks drift per-chapter: $\Delta W_c > 6.0$ triggers a warning. But a drift of 4.0 per chapter across 30 chapters produces a completely different voice by the end — and no individual chapter triggers the alert.

#### Solution: Exponential Moving Average

$$\text{CumulativeDrift}(C_k) = \alpha \cdot \text{CompositeDrift}(C_k) + (1 - \alpha) \cdot \text{CumulativeDrift}(C_{k-1})$$

Where $\alpha = 0.1$ (tunable per project).

```python
def compute_cumulative_drift(
    book_id: int,
    chapter_id: int,
    measured_features: dict,
    profile: dict,       # from catalog_personas.voice_dna_json
    evolution: dict | None,  # from voice_evolution if active
    alpha: float = 0.1
) -> DriftResult:
    """
    Compute per-feature drift, composite drift, and cumulative EMA.
    """
    # 1. Get target features (profile, adjusted by evolution if applicable)
    target = profile.copy()
    evolution_applied = False
    if evolution and chapter_in_range(chapter_id, evolution):
        target = apply_evolution_overrides(target, evolution["adjustedFeatures"])
        evolution_applied = True
    
    # 2. Per-feature drift (normalized distance from tolerance band)
    per_feature = {}
    for feat_name, target_val in target.items():
        if feat_name not in measured_features:
            continue
        measured_val = measured_features[feat_name]
        tolerance = FEATURE_TOLERANCES.get(feat_name, (0.8, 1.2))  # ±20% default
        
        if isinstance(target_val, (int, float)):
            low, high = target_val * tolerance[0], target_val * tolerance[1]
            if low <= measured_val <= high:
                per_feature[feat_name] = 0.0
            else:
                distance = min(abs(measured_val - low), abs(measured_val - high))
                range_size = high - low
                per_feature[feat_name] = min(distance / max(range_size, 0.01), 1.0)
    
    # 3. Weighted composite
    weights = GENRE_FEATURE_WEIGHTS[get_pipeline_mode(book_id)]
    composite = sum(
        per_feature.get(f, 0) * weights.get(f, 1.0) for f in per_feature
    ) / sum(weights.get(f, 1.0) for f in per_feature)
    
    # 4. Cumulative EMA
    prev = get_latest_cumulative_drift(book_id)  # from voice_drift_log
    cumulative = alpha * composite + (1 - alpha) * (prev or 0.0)
    
    # 5. Flagging
    flagged = (composite > 0.30 or cumulative > 0.20) and not evolution_applied
    
    return DriftResult(
        per_feature_drift=per_feature,
        composite_drift=composite,
        cumulative_drift=cumulative,
        flagged=flagged,
        evolution_applied=evolution_applied,
        worst_features=sorted(per_feature.items(), key=lambda x: -x[1])[:3]
    )
```

#### Genre-Specific Feature Weights

| Feature | Literary Fiction | Commercial Thriller | Romance | Non-Fiction |
|:---|:---:|:---:|:---:|:---:|
| `sentence_len_sd` (burstiness) | 1.5 | 1.0 | 0.8 | 1.0 |
| `figurative_density` | 2.0 | 0.5 | 1.0 | 0.3 |
| `dialogue_ratio` | 0.8 | 1.5 | 1.8 | 0.2 |
| `contraction_freq` | 1.0 | 1.2 | 1.5 | 0.8 |
| `passive_voice_freq` | 1.5 | 1.0 | 0.5 | 1.5 |
| `pov_consistency` | 2.0 | 2.0 | 2.0 | 1.0 |
| `tense_consistency` | 2.0 | 2.0 | 2.0 | 2.0 |
| `sentence_opener_variety` | 1.5 | 1.0 | 1.0 | 0.8 |
| `show_tell_ratio` | 2.0 | 1.5 | 1.5 | 0.3 |

### 1.4 v3 Enhancement 3: Intentional Voice Evolution

Authors may intentionally shift voice across a book (darker tone as stakes rise, shorter sentences in action sequences). The `voice_evolution` table stores author-declared shifts:

```python
def check_evolution_coverage(book_id: int, chapter_number: int) -> dict | None:
    """
    Check if any voice_evolution entry covers this chapter.
    Returns the adjusted features dict, or None.
    """
    rows = db.query(
        """SELECT adjustedFeatures, reason FROM voice_evolution
           WHERE bookId = ? AND chapterStart <= ? 
           AND (chapterEnd IS NULL OR chapterEnd >= ?)""",
        book_id, chapter_number, chapter_number
    )
    if rows:
        return json.loads(rows[0]["adjustedFeatures"])
    return None
```

When an evolution is active, drift calculation uses the *adjusted* target features instead of the base persona profile, and the `evolutionApplied` flag is set so the UI shows "intentional shift" instead of "drift warning."

### 1.5 v3 Enhancement 4: Dialogue Sub-Engine

#### New Role: `dialogue_director.md`

Bound to `WEAVER_5_SCENE_COMPOSER`. Activated when a scene plan indicates multi-character dialogue.

**Responsibilities:**
1. **Conversation arc planning** — Structure the dialogue as escalation: opening stakes → conflict → revelation/decision → exit hook
2. **Per-character voice enforcement** — Inject each character's `speechFingerprint` into generation constraints
3. **Subtext calibration** — For characters with high `says_vs_means_gap`, generate dialogue where surface meaning ≠ actual intent
4. **Pairwise voice similarity check** — Post-generation, compute feature similarity between character dialogue samples; flag if cosine similarity > 0.70
5. **Tag/beat rhythm** — Enforce variety in dialogue attribution (said vs. action beats vs. internal thought)

#### Character Voice Differentiation Check

```python
def check_dialogue_differentiation(
    text: str,
    characters: list[dict]  # from character_registry with speechFingerprint
) -> list[dict]:
    """
    Extract dialogue per character and compute pairwise similarity.
    Flag pairs with similarity > 0.70.
    """
    dialogue_by_char = extract_attributed_dialogue(text, characters)
    warnings = []
    
    for i, (char_a, text_a) in enumerate(dialogue_by_char.items()):
        features_a = extract_dialogue_features(text_a)  # subset of full features
        for char_b, text_b in list(dialogue_by_char.items())[i+1:]:
            features_b = extract_dialogue_features(text_b)
            similarity = cosine_similarity(features_a, features_b)
            if similarity > 0.70:
                warnings.append({
                    "type": "DIALOGUE_VOICE_COLLAPSE",
                    "characters": [char_a, char_b],
                    "similarity": similarity,
                    "severity": "warning"
                })
    
    return warnings
```

---

## 2. Context Orchestrator Enhancements (`rag_engine.py` + `orchestrator.js`)

### 2.1 Current State (v2.1)

v2.1 provides:
- Point-in-time RAG with epistemic isolation (`chapter_number <= k AND writer_confirmed = 1`)
- Hybrid SQLite FTS + LanceDB vector retrieval
- Flat 4,500 token context cap per agent invocation
- Strictly scoped context: active characters, location rules, immediate prior beat, open Chekhov Guns, due questions, persona DNA

### 2.2 v3 Enhancement 1: Token Budget Allocation (ADR-0014)

Replace the flat 4,500 token cap with percentage-based allocation across 9 context layers, adjusted per stage.

#### Context Layer Architecture

```text
┌─────────────────────────────────────────────────────────────────┐
│                    MODEL CONTEXT WINDOW                         │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Layer 1: System Instructions (5%)                        │   │
│  │ Layer 2: Voice Profile / Persona DNA (5%)                │   │
│  │ Layer 3: Scene Plan / Beat Sheet (5%)                    │   │
│  │ Layer 4: Recent Manuscript (sliding window) (25%)        │   │
│  │ Layer 5: Outline Context (10%)                           │   │
│  │ Layer 6: Worldbible / Characters / Rules (15%)           │   │
│  │ Layer 7: Retrieved Context (RAG results) (15%)           │   │
│  │ Layer 8: Editorial Notes / Open Issues (5%)              │   │
│  │ Layer 9: Foreshadowing Ledger (5%)                       │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ Buffer (10%)                                             │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ Reserved for Output (generation)                         │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

#### Stage-Specific Rebalancing

| Layer | L4 Scene Composer | L5 Style Polish | L6 Continuity | Dev Edit |
|:---|:---:|:---:|:---:|:---:|
| System instructions | 5% | 5% | 5% | 5% |
| Voice profile | 5% | 10% | 3% | 3% |
| Scene plan | 8% | 3% | 3% | 3% |
| Recent manuscript | 25% | 15% | 35% | 40% |
| Outline | 10% | 5% | 5% | 5% |
| Worldbible | 15% | 5% | 20% | 5% |
| Retrieved (RAG) | 12% | 7% | 10% | 15% |
| Editorial notes | 5% | 5% | 10% | 15% |
| Foreshadowing | 5% | 5% | 5% | 3% |
| Buffer | 10% | 40% | 4% | 6% |

```python
STAGE_ALLOCATIONS = {
    "L4_SCENE_COMPOSER": {
        "system": 0.05, "voice": 0.05, "scene_plan": 0.08,
        "recent_manuscript": 0.25, "outline": 0.10, "worldbible": 0.15,
        "retrieved": 0.12, "editorial": 0.05, "foreshadowing": 0.05, "buffer": 0.10
    },
    "L5_STYLE_POLISHER": {
        "system": 0.05, "voice": 0.10, "scene_plan": 0.03,
        "recent_manuscript": 0.15, "outline": 0.05, "worldbible": 0.05,
        "retrieved": 0.07, "editorial": 0.05, "foreshadowing": 0.05, "buffer": 0.40
    },
    # L5 has large buffer because it receives the full generated text as input
    "L6_CONTINUITY_MANAGER": {
        "system": 0.05, "voice": 0.03, "scene_plan": 0.03,
        "recent_manuscript": 0.35, "outline": 0.05, "worldbible": 0.20,
        "retrieved": 0.10, "editorial": 0.10, "foreshadowing": 0.05, "buffer": 0.04
    },
}

def allocate_token_budget(stage: str, model_context: int, output_reserve: int) -> dict:
    available = model_context - output_reserve
    alloc = STAGE_ALLOCATIONS.get(stage, STAGE_ALLOCATIONS["L4_SCENE_COMPOSER"])
    return {layer: int(available * pct) for layer, pct in alloc.items()}
```

### 2.3 v3 Enhancement 2: Hierarchical Summarization with Freshness Decay

For the "recent manuscript" layer, use a freshness-ranked sliding window:

| Distance from Current | Summary Level | Compression | Token Cost |
|:---|:---|:---|:---|
| Previous chapter (N-1) | `full` | None (original text) | Full |
| N-2 | `medium` | ~30% of original | ~30% |
| N-3 to N-5 | `brief` | ~10% of original | ~10% |
| N-6+ | `oneliner` | 1-2 sentences | ~2% |

```python
FRESHNESS_LEVELS = [
    (1, "full"),      # Previous chapter
    (2, "medium"),    # Two back
    (3, "brief"),     # Three to five back
    (4, "brief"),
    (5, "brief"),
]
# All chapters beyond offset 5 get "oneliner"

def assemble_recent_manuscript(
    book_id: int,
    current_chapter: int,
    max_tokens: int
) -> str:
    """
    Assemble recent manuscript context with freshness decay.
    Most recent content gets the most detail.
    """
    parts = []
    remaining = max_tokens
    
    for offset in range(1, current_chapter):
        ch_num = current_chapter - offset
        
        # Determine summary level based on freshness
        if offset <= len(FRESHNESS_LEVELS):
            level = FRESHNESS_LEVELS[offset - 1][1]
        else:
            level = "oneliner"
        
        summary = get_or_create_summary(book_id, f"chapter:{ch_num}", level)
        
        if summary["tokenCount"] <= remaining:
            parts.append(f"--- Chapter {ch_num} ({level}) ---\n{summary['summaryText']}")
            remaining -= summary["tokenCount"]
        else:
            # Try shorter level
            shorter = LEVEL_DOWNGRADE.get(level)
            if shorter:
                summary = get_or_create_summary(book_id, f"chapter:{ch_num}", shorter)
                if summary["tokenCount"] <= remaining:
                    parts.append(f"--- Chapter {ch_num} ({shorter}) ---\n{summary['summaryText']}")
                    remaining -= summary["tokenCount"]
        
        if remaining <= 0:
            break
    
    return "\n\n".join(parts)


LEVEL_DOWNGRADE = {"full": "medium", "medium": "brief", "brief": "oneliner"}

SUMMARY_COMPRESSION = {
    "full": None,       # no compression
    "medium": 0.30,     # ~30% of original
    "brief": 0.10,      # ~10% of original
    "oneliner": 0.02,   # 1-2 sentences
}

def generate_summary(text: str, level: str, model: str = "fast_cheap") -> str:
    """
    Generate a summary at the specified compression level.
    Uses a cheaper model to minimize token cost.
    """
    if level == "full":
        return text
    
    target_ratio = SUMMARY_COMPRESSION[level]
    target_tokens = int(count_tokens(text) * target_ratio)
    
    return call_llm(
        model=model,
        system=(
            "You are a precise book summarizer. PRESERVE: character names, "
            "plot events, emotional beats, foreshadowing, unresolved tensions, "
            "key decisions, relationship changes. OMIT: descriptive prose, "
            "dialogue details, atmospheric passages."
        ),
        prompt=f"Summarize to ~{target_tokens} tokens:\n\n{text}",
        max_tokens=target_tokens + 100
    )
```

### 2.4 Summary Cache Invalidation

```python
def get_or_create_summary(book_id: int, scope: str, level: str) -> dict:
    """
    Return cached summary if fresh, or generate and cache a new one.
    Freshness is determined by source_hash comparison.
    """
    cached = db.query(
        "SELECT * FROM summary_cache WHERE bookId=? AND scope=? AND level=?",
        book_id, scope, level
    )
    
    current_hash = compute_source_hash(book_id, scope)
    
    if cached and cached[0]["sourceHash"] == current_hash:
        return cached[0]  # Cache hit
    
    # Cache miss or stale: regenerate
    source_text = get_source_text(book_id, scope)
    summary_text = generate_summary(source_text, level)
    
    db.upsert("summary_cache", {
        "bookId": book_id,
        "scope": scope,
        "level": level,
        "summaryText": summary_text,
        "tokenCount": count_tokens(summary_text),
        "sourceHash": current_hash,
        "modelUsed": "fast_cheap",
    }, conflict_keys=["bookId", "scope", "level"])
    
    return {"summaryText": summary_text, "tokenCount": count_tokens(summary_text)}
```

### 2.5 Full Context Assembly Flow

```python
def assemble_context(
    book_id: int,
    stage: str,
    chapter_number: int,
    beat_index: int | None,
    model_spec: dict  # {name, max_context, output_reserve}
) -> dict:
    """
    Assemble the complete context prefix for an LLM invocation.
    Enforces Invariant 16: total tokens <= model.max_context - output_reserve.
    """
    budget = allocate_token_budget(stage, model_spec["max_context"], model_spec["output_reserve"])
    layers = {}
    
    # Layer 1: System instructions
    layers["system"] = truncate(get_system_instructions(book_id, stage), budget["system"])
    
    # Layer 2: Voice profile / Persona DNA
    persona = get_persona_dna(book_id)
    evolution = check_evolution_coverage(book_id, chapter_number)
    layers["voice"] = truncate(format_voice_profile(persona, evolution), budget["voice"])
    
    # Layer 3: Scene plan / Beat sheet
    if beat_index:
        layers["scene_plan"] = truncate(get_beat_worksheet(book_id, chapter_number, beat_index), budget["scene_plan"])
    else:
        layers["scene_plan"] = truncate(get_chapter_worksheet(book_id, chapter_number), budget["scene_plan"])
    
    # Layer 4: Recent manuscript (hierarchical, freshness-ranked)
    layers["recent_manuscript"] = assemble_recent_manuscript(book_id, chapter_number, budget["recent_manuscript"])
    
    # Layer 5: Outline context
    layers["outline"] = truncate(get_outline_section(book_id, chapter_number), budget["outline"])
    
    # Layer 6: Worldbible (characters, locations, rules relevant to this scene)
    layers["worldbible"] = assemble_worldbible_context(
        book_id, chapter_number, beat_index, budget["worldbible"]
    )
    
    # Layer 7: Retrieved context (RAG — respects epistemic isolation: Invariant 12)
    query = build_retrieval_query(book_id, chapter_number, beat_index)
    layers["retrieved"] = retrieve_relevant_chunks(
        book_id, query, max_tokens=budget["retrieved"],
        filter=f"chapter_number <= {chapter_number} AND writer_confirmed = 1"
    )
    
    # Layer 8: Editorial notes
    layers["editorial"] = truncate(
        get_editorial_context(book_id, chapter_number), budget["editorial"]
    )
    
    # Layer 9: Foreshadowing ledger
    layers["foreshadowing"] = truncate(
        format_open_promises(book_id, chapter_number), budget["foreshadowing"]
    )
    
    # Assemble and verify budget
    total_tokens = sum(count_tokens(v) for v in layers.values())
    available = model_spec["max_context"] - model_spec["output_reserve"]
    
    if total_tokens > available:
        layers = compress_overflow(layers, available)
        total_tokens = sum(count_tokens(v) for v in layers.values())
    
    # Log to context_budget_log
    log_context_budget(book_id, stage, chapter_number, beat_index,
                       model_spec, layers, total_tokens, available)
    
    prompt_prefix = "\n\n---\n\n".join(
        f"[{name.upper()}]\n{text}" for name, text in layers.items() if text
    )
    
    return {
        "prompt_prefix": prompt_prefix,
        "token_count": total_tokens,
        "budget_utilization": total_tokens / available,
        "layers": {k: count_tokens(v) for k, v in layers.items()}
    }
```

---

## 3. New Modules

### 3.1 Research Vault (`py_core/research_vault.py`) — ADR-0015

```python
class ResearchVault:
    """
    External source management for fact-checking and citation.
    Primary use: non-fiction (FMT_07-FMT_12), historical fiction, hard sci-fi.
    """
    
    def ingest_source(self, book_id: int, source_type: str, 
                       title: str, content: str, **kwargs) -> int:
        """Add a research source and extract facts."""
        source_id = db.insert("research_sources", {
            "bookId": book_id,
            "sourceType": source_type,
            "title": title,
            "contentSnippet": content[:2000],
            **kwargs
        })
        # Auto-extract facts using LLM
        facts = self._extract_facts(content, source_type)
        for fact in facts:
            db.insert("research_facts", {
                "bookId": book_id,
                "sourceId": source_id,
                "factText": fact["text"],
                "category": fact.get("category"),
                "verified": 0,
                "confidence": fact.get("confidence", 0.5)
            })
        return source_id
    
    def verify_chapter_facts(self, book_id: int, chapter_number: int) -> list:
        """Cross-check facts cited in a chapter against stored sources."""
        chapter_facts = db.query(
            """SELECT rf.* FROM research_facts rf
               WHERE rf.bookId = ? AND rf.chapterRefs LIKE ?""",
            book_id, f'%{chapter_number}%'
        )
        unverified = [f for f in chapter_facts if not f["verified"]]
        return unverified
    
    def generate_bibliography(self, book_id: int, style: str = "chicago") -> str:
        """Generate formatted bibliography from all verified sources."""
        sources = db.query(
            "SELECT * FROM research_sources WHERE bookId = ? ORDER BY title",
            book_id
        )
        return format_bibliography(sources, style)
```

### 3.2 Compliance Gate (`py_core/compliance_gate.py`) — ADR-0016

```python
class ComplianceGate:
    """
    Pre-publication compliance checks.
    Blocks retail compilation if blocking findings exist (Invariant 18).
    """
    
    CHECK_TYPES = ["plagiarism", "trademark", "sensitivity", "ai_disclosure"]
    
    def run_all_checks(self, book_id: int) -> dict:
        results = {}
        for check_type in self.CHECK_TYPES:
            result = getattr(self, f"check_{check_type}")(book_id)
            db.insert("compliance_checks", {
                "bookId": book_id,
                "checkType": check_type,
                "status": result["status"],
                "findings": json.dumps(result.get("findings", [])),
                "recommendedActions": json.dumps(result.get("actions", [])),
            })
            results[check_type] = result
        return results
    
    def check_plagiarism(self, book_id: int) -> dict:
        """Check for high-similarity passages against known works."""
        # N-gram fingerprinting against reference corpus
        # Flag passages with >70% 5-gram overlap
        pass
    
    def check_trademark(self, book_id: int) -> dict:
        """Screen character/place names against trademark databases."""
        pass
    
    def check_sensitivity(self, book_id: int) -> dict:
        """Flag potentially harmful stereotypes or representations."""
        pass
    
    def check_ai_disclosure(self, book_id: int) -> dict:
        """Ensure AI-assistance disclosure metadata is present."""
        config = load_config(book_id)
        if not config.get("ai_disclosure_acknowledged"):
            return {"status": "blocking", "findings": ["AI disclosure not acknowledged"]}
        return {"status": "pass"}
    
    def is_export_allowed(self, book_id: int) -> bool:
        """Invariant 18: No blocking findings may exist before export."""
        blocking = db.query(
            """SELECT COUNT(*) as cnt FROM compliance_checks
               WHERE bookId = ? AND status = 'blocking' AND resolved = 0""",
            book_id
        )
        return blocking[0]["cnt"] == 0
```

### 3.3 Token Budget Engine (`py_core/token_budget.py`) — ADR-0014

```python
class TokenBudgetEngine:
    """
    Estimates cost, selects optimal model per task, and tracks spend.
    """
    
    MODEL_COSTS = {
        # per 1M tokens (input / output)
        "gpt-4o": (2.50, 10.00),
        "gpt-4o-mini": (0.15, 0.60),
        "gemini-1.5-flash": (0.075, 0.30),
        "gemini-1.5-pro": (1.25, 5.00),
        "ollama/local": (0.0, 0.0),
    }
    
    STAGE_MODEL_PREFERENCE = {
        # Creative generation: use best available
        "L4_SCENE_COMPOSER": "quality",
        # Style polish: use best available
        "L5_STYLE_POLISHER": "quality",
        # Summarization: use cheapest
        "SUMMARIZER": "economy",
        # Continuity checking: mid-tier
        "L6_CONTINUITY_MANAGER": "balanced",
        # Research/compliance: mid-tier
        "COMPLIANCE": "balanced",
    }
    
    def estimate_stage_cost(self, book_id: int, stage: str,
                             chapter_count: int = 1) -> dict:
        """Estimate token cost before starting a stage."""
        avg_context = self._avg_context_tokens(book_id, stage)
        avg_output = self._avg_output_tokens(book_id, stage)
        preference = self.STAGE_MODEL_PREFERENCE.get(stage, "balanced")
        model = self._select_model(preference)
        
        input_cost = (avg_context * chapter_count / 1_000_000) * self.MODEL_COSTS[model][0]
        output_cost = (avg_output * chapter_count / 1_000_000) * self.MODEL_COSTS[model][1]
        
        return {
            "model": model,
            "estimated_input_tokens": avg_context * chapter_count,
            "estimated_output_tokens": avg_output * chapter_count,
            "estimated_cost_usd": input_cost + output_cost,
        }
    
    def log_spend(self, book_id: int, stage: str, model: str, provider: str,
                   prompt_tokens: int, completion_tokens: int, **kwargs):
        """Record actual token consumption."""
        cost = self._compute_cost(model, prompt_tokens, completion_tokens)
        db.insert("token_spend_ledger", {
            "bookId": book_id,
            "stage": stage,
            "model": model,
            "provider": provider,
            "promptTokens": prompt_tokens,
            "completionTokens": completion_tokens,
            "totalTokens": prompt_tokens + completion_tokens,
            "estimatedCostUsd": cost,
            **kwargs
        })
    
    def get_project_spend(self, book_id: int) -> dict:
        """Total spend report for a project."""
        rows = db.query(
            """SELECT stage, model, SUM(totalTokens) as tokens,
                      SUM(estimatedCostUsd) as cost
               FROM token_spend_ledger WHERE bookId = ?
               GROUP BY stage, model""",
            book_id
        )
        return {
            "by_stage": rows,
            "total_tokens": sum(r["tokens"] for r in rows),
            "total_cost_usd": sum(r["cost"] or 0 for r in rows),
        }
```

---

## 4. Extended Quality Gates

### 4.1 Complete Gate Matrix (v2.1 + v3)

| Gate ID | Criterion | Threshold | Severity | Source |
|:---|:---|:---|:---|:---|
| `writer_sovereignty` | `writer_confirmed = 1` before RAG/ledger/export | Mandatory | **Blocking** | ADR-0001 |
| `beat_lock_integrity` | LOCKED beats unchanged after surgical rewrite | Byte-identical | **Blocking** | ADR-0012 |
| `scorecard_veto` | clarity ≠ 0 AND payoff ≠ 0 AND total ≥ 10 | Hard veto | **Blocking** | Invariant 3 |
| `burstiness_floor` | $\sigma_S \ge 4.2$ (15-sentence window) | ≥ 4.2 | **Blocking** | Invariant 5 |
| `filter_density_ceiling` | Class-2 filter words per 1k | < 3.0 | **Blocking** | Invariant 6 |
| `opening_hook` | Ch 1–5 first 200-400w contains interest source | ≥ 1 source | **Blocking** | Invariant 7 |
| `anti_withholding` | Paywall chapter resolves ≥1 question from Ch 1–2 | ≥ 1 resolution | **Blocking** | Invariant 8 |
| `ending_rotation` | No consecutive same high-intensity ending | Rotation law | **Warning** | Invariant 9 |
| `chekhov_orphan` | No ORPHANED entries at final chapter | 0 orphaned | **Blocking** (export) | Invariant 10 |
| `dirty_tree_propagation` | Downstream chapters marked dirty on upstream edit | Automatic | **Info** | Invariant 11 |
| `epistemic_isolation` | RAG filtered to `chapter <= k AND confirmed` | Mandatory | **Blocking** | Invariant 12 |
| `kdp_gutter` | Print margins match page count ladder | Measured | **Blocking** (print) | Invariant 13 |
| `acx_mastering` | -18 LUFS, -3.1 dBFS peak, -60 dB floor | Measured | **Blocking** (audio) | Invariant 14 |
| `cumulative_drift` | EMA drift ≤ 0.20 (unless evolution active) | ≤ 0.20 | **Warning** | **Invariant 15 [v3]** |
| `context_budget_fit` | Assembled context ≤ model limit - output reserve | 100% | **Blocking** | **Invariant 16 [v3]** |
| `nonfiction_citations` | All citations verified for NONFICTION_AUTHORITY | 0 unverified | **Blocking** (export) | **Invariant 17 [v3]** |
| `compliance_clear` | No blocking compliance findings | 0 blocking | **Blocking** (export) | **Invariant 18 [v3]** |
| `composite_drift` | Per-scene composite drift | < 0.30 | **Warning** | v3 |
| `pov_consistency` | No POV shifts within scene | 0 violations | **Warning** | v3 |
| `tense_consistency` | No tense shifts within scene | 0 violations | **Warning** | v3 |
| `dialogue_differentiation` | Pairwise character similarity | < 0.70 | **Warning** | v3 |
| `opener_variety` | Consecutive same-opener count | ≤ 2 | **Warning** | v3 |
| `cliche_density` | 154-pattern hits per beat | ≤ 2 unwaived | **Warning** | v2.1 |

---

*End of Engine Enhancement Specifications.*
