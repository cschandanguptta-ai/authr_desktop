package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.container.AuthrContainer
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.Lorebook
import com.example.data.repository.AppRepository
import com.example.engine.audio.AudiobookRenderer
import com.example.engine.audio.PronunciationPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The Audiobook workbench state machine (v3.0 D-13, the P4 report handoff).
 *
 * Hosts the P4 engine — [com.example.engine.audio.AudiobookRenderer], the
 * pronunciation pass, the mastering chain, the ACX audit — in the app
 * (until this screen the renderer was engine-only, proven by the P4 gate
 * but unreachable from the UI; the AudioScreen still plays per-paragraph
 * TTS through StudioViewModel):
 *
 *  1. engine picker: Pocket TTS (POCKET_STRUCTURAL, the production engine)
 *     when the binary + voice pack resolve, espeak-ng (ESPEAK_LOCAL, the
 *     bundled offline engine) as the deterministic fallback — availability
 *     probed where the Context lives (Main.kt), never inside the VM;
 *  2. the pronunciation review (plan §7.3): scan the confirmed chapters
 *     (lorebook terms first), propose respellings from the deterministic
 *     table, approve/revoke — approvals stage into the per-book container
 *     location `authr/<bookId>/audio/pronunciation_rules.json` (the same
 *     entry a real .authr export writes);
 *  3. one render action: [AudiobookRenderer.render] with the approved
 *     rules, on the injected renderer factory — the same pipeline the P4
 *     gate ran end to end;
 *  4. the outcome: the render report — per-chapter audio seconds, ACX
 *     verdicts, SMIL timing, master audit, the synthesis-speed factor —
 *     visible where the writer works.
 *
 * Deliberately Context-free (the PublishingViewModel pattern): every
 * computation runs on engine code and is unit-testable on the JVM with
 * fakes (AudiobookWorkbenchTest renders two chapters through a fake
 * synthesizer + fake mastering + the injected audit runner — no binaries,
 * no Room, no Context shim).
 */
class AudiobookViewModel(
    private val repository: AppRepository,
    /** App data root for rule staging + render output; null disables persistence (tests). */
    private val authrRoot: File? = null,
    /** The engine catalog, probed outside (Main.kt owns the Context). */
    private val engineCatalog: List<EngineOption>,
    /** Builds the synthesizer for an engine id. */
    private val synthesizerFor: (String) -> AudiobookRenderer.Synthesizer,
    /** The IPA source for the pronunciation scan; null disables scanning with a loud status. */
    private val phonemizer: PronunciationPass.Phonemizer? = null,
    /** Builds the renderer around a synthesizer (mastering + audit wiring stays outside). */
    private val rendererFor: (AudiobookRenderer.Synthesizer) -> AudiobookRenderer,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel() {

    /** One engine the picker may offer. */
    data class EngineOption(
        val id: String,
        val label: String,
        val continuity: String,
        val available: Boolean,
        val unavailableNote: String? = null
    )

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Int?>(null)
    val selectedBookId: StateFlow<Int?> = _selectedBookId.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _lorebook = MutableStateFlow<List<Lorebook>>(emptyList())
    val lorebook: StateFlow<List<Lorebook>> = _lorebook.asStateFlow()

    private val _engines = MutableStateFlow(engineCatalog)
    val engines: StateFlow<List<EngineOption>> = _engines.asStateFlow()

    private val _selectedEngineId = MutableStateFlow(
        engineCatalog.firstOrNull { it.available }?.id ?: engineCatalog.firstOrNull()?.id ?: ""
    )
    val selectedEngineId: StateFlow<String> = _selectedEngineId.asStateFlow()

    /** Scan results awaiting the writer's approval. */
    private val _candidates = MutableStateFlow<List<PronunciationPass.Candidate>>(emptyList())
    val candidates: StateFlow<List<PronunciationPass.Candidate>> = _candidates.asStateFlow()

    private val _approved = MutableStateFlow<List<PronunciationPass.ApprovedRule>>(emptyList())
    val approved: StateFlow<List<PronunciationPass.ApprovedRule>> = _approved.asStateFlow()

    private val _rendering = MutableStateFlow(false)
    val rendering: StateFlow<Boolean> = _rendering.asStateFlow()

    /** The last render report; null until the first render. */
    private val _report = MutableStateFlow<AudiobookRenderer.RenderReport?>(null)
    val report: StateFlow<AudiobookRenderer.RenderReport?> = _report.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /** The extracted-container staging root for the pronunciation rules (the P4 container entry). */
    fun stagingRoot(bookId: Int): File? =
        authrRoot?.let { File(File(it, "authr"), bookId.toString()) }

    fun refreshBooks() {
        scope.launch {
            _books.value = repository.getBooksSync()
        }
    }

    fun selectBook(bookId: Int) {
        scope.launch {
            _selectedBookId.value = bookId
            _chapters.value = repository.getChaptersForBookSync(bookId)
            _lorebook.value = repository.getLorebookForBookSync(bookId)
            _report.value = null
            _candidates.value = emptyList()
            // Staged rules are authoritative (the OQ-9 pattern): a real
            // .authr export seeds this entry; the workbench keeps it fresh.
            val staged = stagingRoot(bookId)?.let { PronunciationStore.read(it) }
            _approved.value = staged ?: emptyList()
            _status.value = if (staged != null) {
                "${staged.size} approved pronunciation rule(s) loaded from the staged container."
            } else {
                "Scan the confirmed chapters for coined terms, approve the respellings, then render."
            }
        }
    }

    fun selectEngine(id: String) {
        val engine = _engines.value.firstOrNull { it.id == id } ?: return
        if (!engine.available) {
            _status.value = engine.unavailableNote ?: "Engine unavailable."
            return
        }
        _selectedEngineId.value = id
        _status.value = "Engine: ${engine.label} — ${engine.continuity}"
    }

    /** Plan §7.3 step 1: scan the chapters for coined/proper/lorebook terms. */
    fun scanPronunciation() {
        val ph = phonemizer
        if (ph == null) {
            _status.value = "Pronunciation scan needs a phonemizer (espeak-ng); engine unavailable."
            return
        }
        val chapters = _chapters.value
        if (chapters.isEmpty()) {
            _status.value = "Select a book with chapters first."
            return
        }
        val found = PronunciationPass.scan(
            chapters = chapters,
            lorebookTerms = _lorebook.value.map { it.key },
            phonemizer = ph
        )
        _candidates.value = found
        _status.value = if (found.isEmpty()) {
            "No pronunciation candidates — every term is common English."
        } else {
            "${found.size} candidate(s); review the IPA → respelling pairs and approve."
        }
    }

    /** Approves one candidate (and stages the rule immediately — approval is the writer's deliberate act). */
    fun approve(candidate: PronunciationPass.Candidate) {
        val rule = PronunciationPass.ApprovedRule(candidate.term, candidate.ipa, candidate.respelling)
        _approved.value = (_approved.value.filter { !it.term.equals(rule.term, ignoreCase = true) } + rule)
            .sortedBy { it.term.lowercase() }
        _candidates.value = _candidates.value.filter { !it.term.equals(candidate.term, ignoreCase = true) }
        persistRules("Approved '${rule.term}' → ${rule.respelling}.")
    }

    fun approveAll() {
        val rules = _candidates.value.map { PronunciationPass.ApprovedRule(it.term, it.ipa, it.respelling) }
        if (rules.isEmpty()) return
        _approved.value = (_approved.value + rules)
            .distinctBy { it.term.lowercase() }
            .sortedBy { it.term.lowercase() }
        _candidates.value = emptyList()
        persistRules("Approved ${rules.size} rule(s).")
    }

    fun revoke(term: String) {
        _approved.value = _approved.value.filter { !it.term.equals(term, ignoreCase = true) }
        persistRules("Revoked '$term'.")
    }

    private fun persistRules(note: String) {
        // Async for the UI path; failures (e.g. the test root vanishing
        // before the launch runs) stay contained — the next approval re-stages.
        scope.launch {
            runCatching { persistRulesNow() }
            _status.value = note
        }
    }

    /** The staging core; exposed suspend so tests run it deterministically. */
    suspend fun persistRulesNow() {
        val bookId = _selectedBookId.value ?: return
        val title = _books.value.firstOrNull { it.id == bookId }?.title ?: ""
        val root = stagingRoot(bookId) ?: return
        withContext(Dispatchers.IO) {
            PronunciationStore.write(root, title, _approved.value)
        }
    }

    /** One action: render the confirmed chapters with the approved rules (the P4 pipeline). */
    fun renderAudiobook() {
        scope.launch { renderNow() }
    }

    /** The render core; exposed suspend so tests run it deterministically. */
    suspend fun renderNow() {
        val bookId = _selectedBookId.value ?: return
        val book = _books.value.firstOrNull { it.id == bookId } ?: return
        val engineId = _selectedEngineId.value
        val engine = _engines.value.firstOrNull { it.id == engineId }
        if (engine == null || !engine.available) {
            _status.value = "Pick an available engine first."
            return
        }
        val root = authrRoot
        if (root == null) {
            _status.value = "Rendering needs a data root (tests run the engine gate instead)."
            return
        }
        _rendering.value = true
        _status.value = "Rendering '${book.title}' with ${engine.label} — synthesis, mastering, ACX audit, SMIL…"
        val outcome = runCatching {
            withContext(Dispatchers.Default) {
                val workDir = File(File(root, "audiobooks"), slug(book.title))
                val renderer = rendererFor(synthesizerFor(engineId))
                renderer.render(
                    projectTitle = book.title,
                    chapters = _chapters.value,
                    workDir = workDir,
                    approvedRules = _approved.value
                )
            }
        }
        _rendering.value = false
        outcome.fold(
            onSuccess = { report ->
                _report.value = report
                _status.value = if (report.allGreen) {
                    "Render ALL GREEN — %.2f× real time; upload the MP3s + SMIL.".format(report.synthesisSpeedFactor)
                } else {
                    "Render finished with findings — read the report before uploading."
                }
            },
            onFailure = {
                _status.value = "Render failed: ${it.message}"
            }
        )
    }

    private fun slug(title: String): String =
        title.replace(Regex("[^a-zA-Z0-9.-]"), "_").ifBlank { "project" }
}

/**
 * Staged-container persistence for the pronunciation rules — the same
 * entry a real .authr export writes (`audio/pronunciation_rules.json`),
 * mirroring [com.example.engine.publishing.WorkbenchStore].
 */
object PronunciationStore {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    const val CONTAINER_ENTRY = "audio/pronunciation_rules.json"

    /** Reads the approved rules from an extracted container [root]; null when absent. */
    fun read(root: File): List<PronunciationPass.ApprovedRule>? {
        val f = File(root, CONTAINER_ENTRY)
        if (!f.exists()) return null
        val text = f.readText().trim()
        if (text.isEmpty()) return null
        return try {
            PronunciationPass.approvedFromContainer(
                json.decodeFromString(AuthrContainer.PronunciationRules.serializer(), text)
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Writes the approved rules into an extracted container [root]. */
    fun write(root: File, projectTitle: String, approved: List<PronunciationPass.ApprovedRule>) {
        val f = File(root, CONTAINER_ENTRY)
        f.parentFile?.mkdirs()
        f.writeText(
            json.encodeToString(
                AuthrContainer.PronunciationRules.serializer(),
                PronunciationPass.toContainerRules(projectTitle, approved)
            )
        )
    }
}
