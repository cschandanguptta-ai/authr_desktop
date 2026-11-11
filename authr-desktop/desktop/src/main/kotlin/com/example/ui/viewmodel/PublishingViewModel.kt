package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChekhovGun
import com.example.data.model.Lorebook
import com.example.data.model.SerialArc
import com.example.data.repository.AppRepository
import com.example.container.AuthrContainer
import com.example.engine.WriterGate
import com.example.engine.publishing.KdpWorkbench
import com.example.engine.publishing.RetailPackager
import com.example.engine.publishing.WorkbenchStore
import com.example.engine.serialization.BufferCadence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Publishing workbench state machine (v3.0 D-15, the P5 report handoff).
 *
 * Hosts the retail side of the OS in the app (until this screen the P5
 * engine — [RetailPackager], the metadata workbench, the ship report — was
 * engine-only, proven by the gate but unreachable from the UI):
 *
 *  1. the metadata workbench editor: the [KdpWorkbench] loaded from the
 *     per-book container staging (`authr/<bookId>/metadata/kdp_workbench.json`,
 *     the OQ-9 authority location) or seeded from the project record via
 *     [KdpWorkbench.fromBook] when the writer never edited it;
 *  2. one-action packaging: export the project snapshot to a real .authr
 *     container, import it back (checksum audit + the gate-proven round
 *     trip), and drive exactly one [RetailPackager.packageRetail] call —
 *     the same order PublishingGate proved end to end (P5);
 *  3. the ship-report outcome: verdicts, artifacts, disclosures and the
 *     clean/not-clean banner, so "if the report is clean, the export behind
 *     it is defensible" (§15) is visible where the writer works.
 *
 * Deliberately Context-free (the SerialDashboardViewModel pattern): every
 * computation runs on engine code and is unit-testable on the JVM; only the
 * staging/export writes touch the file system, through the injected
 * [authrRoot] (null in tests). Packaging runs on [Dispatchers.Default]
 * inside the injected scope, so tests stay synchronous with Unconfined.
 */
class PublishingViewModel(
    private val repository: AppRepository,
    /** App data root for container staging + package output; null disables persistence (tests). */
    private val authrRoot: File? = null,
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel() {

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _selectedBookId = MutableStateFlow<Int?>(null)
    val selectedBookId: StateFlow<Int?> = _selectedBookId.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _arcs = MutableStateFlow<List<SerialArc>>(emptyList())
    val arcs: StateFlow<List<SerialArc>> = _arcs.asStateFlow()

    private val _guns = MutableStateFlow<List<ChekhovGun>>(emptyList())
    val guns: StateFlow<List<ChekhovGun>> = _guns.asStateFlow()

    private val _lorebook = MutableStateFlow<List<Lorebook>>(emptyList())
    val lorebook: StateFlow<List<Lorebook>> = _lorebook.asStateFlow()

    private val _workbench = MutableStateFlow<KdpWorkbench?>(null)
    val workbench: StateFlow<KdpWorkbench?> = _workbench.asStateFlow()

    /** True when the editor diverges from the staged workbench on disk. */
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _packaging = MutableStateFlow(false)
    val packaging: StateFlow<Boolean> = _packaging.asStateFlow()

    /** The last one-action outcome; null until the first package runs. */
    private val _outcome = MutableStateFlow<PackageOutcome?>(null)
    val outcome: StateFlow<PackageOutcome?> = _outcome.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /** One-action result plus the container the package shipped from. */
    data class PackageOutcome(
        val result: RetailPackager.Result,
        val containerFile: File,
        val containerBytes: Long,
        val importedSchemaVersion: Int
    )

    /** The extracted-container staging root for the workbench (OQ-9 location). */
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
            val book = repository.getBookById(bookId) ?: return@launch
            _chapters.value = repository.getChaptersForBookSync(bookId)
            _arcs.value = repository.getArcsForBookSync(bookId)
            _guns.value = repository.getGunsForBookSync(bookId)
            _lorebook.value = repository.getLorebookForBookSync(bookId)
            _outcome.value = null
            // OQ-9: the workbench is authoritative; seed from the project
            // record only when the writer has never staged one.
            val staged = stagingRoot(bookId)?.let { WorkbenchStore.read(it) }
            _workbench.value = staged ?: KdpWorkbench.fromBook(
                title = book.title,
                author = "",
                synopsis = book.synopsis,
                marketingMaterials = book.marketingMaterials
            )
            _dirty.value = false
            _status.value = if (staged != null) {
                "Workbench loaded from the staged container metadata."
            } else {
                "Workbench seeded from the project record — set subtitle, keywords, blurb and pricing."
            }
        }
    }

    /** Applies an edit to the current workbench (the screen passes a copy lambda). */
    fun editWorkbench(transform: (KdpWorkbench) -> KdpWorkbench) {
        val current = _workbench.value ?: return
        _workbench.value = transform(current)
        _dirty.value = true
    }

    /** Stages the workbench into the extracted container layout (OQ-9 location). */
    fun saveWorkbench() {
        scope.launch { saveWorkbenchNow() }
    }

    /** The save core; exposed suspend so tests run it deterministically. */
    suspend fun saveWorkbenchNow() {
        val bookId = _selectedBookId.value ?: return
        val wb = _workbench.value ?: return
        val root = stagingRoot(bookId)
        if (root == null) {
            _status.value = "No container root available (tests) — workbench kept in memory."
            _dirty.value = false
            return
        }
        withContext(Dispatchers.IO) {
            WorkbenchStore.write(root, wb)
        }
        _dirty.value = false
        _status.value = "Workbench staged to authr/$bookId/${KdpWorkbench.CONTAINER_ENTRY}."
    }

    /**
     * The one action (P5 gate order, verbatim): export the snapshot to a
     * real .authr container, import it back (checksum audit), and package
     * from the imported snapshot — so the shipped package provably matches
     * the container a reader/host could receive. The staged workbench rides
     * inside the container (metadata/kdp_workbench.json, OQ-9).
     */
    fun packageRetail() {
        scope.launch { packageRetailNow() }
    }

    /** The packaging core; exposed suspend so tests run it deterministically. */
    suspend fun packageRetailNow() {
        val bookId = _selectedBookId.value ?: return
        val book = _books.value.firstOrNull { it.id == bookId } ?: return
        val wb = _workbench.value ?: return
        val root = authrRoot
        if (root == null) {
            _status.value = "Packaging needs a data root (tests run the engine gate instead)."
            return
        }
        _packaging.value = true
        _status.value = "Packaging ${book.title} — one action: container → import audit → EPUB(s) → epubcheck → print → ship report."
        val outcome = runCatching {
            withContext(Dispatchers.Default) { packageOneAction(book, wb) }
        }
        _packaging.value = false
        outcome.fold(
            onSuccess = {
                _outcome.value = it
                _dirty.value = false
                _status.value = if (it.result.clean) {
                    "Package CLEAN — every gate verdict passed. Upload the EPUB(s) + interior.pdf."
                } else {
                    "Package NOT clean — fix the red verdicts in the ship report before uploading."
                }
            },
            onFailure = {
                _status.value = "Packaging failed: ${it.message}"
            }
        )
    }

    private suspend fun packageOneAction(book: Book, wb: KdpWorkbench): PackageOutcome {
        val chapters = repository.getChaptersForBookSync(book.id)
        val arcs = repository.getArcsForBookSync(book.id)
        val guns = repository.getGunsForBookSync(book.id)
        val lorebook = repository.getLorebookForBookSync(book.id)

        val outDir = File(File(authrRoot!!, "publishing"), slug(book.title))
        outDir.mkdirs()
        val containerFile = File(outDir, "${slug(book.title)}.authr")

        val snapshot = AuthrContainer.ProjectSnapshot(
            book = book,
            chapters = chapters,
            lorebook = lorebook,
            arcs = arcs,
            publishing = wb
        )
        AuthrContainer.export(snapshot, containerFile)
        val imported = AuthrContainer.import(containerFile)
        check(imported.checksumWarnings.isEmpty()) {
            "container checksum audit failed: ${imported.checksumWarnings.size} warning(s)"
        }

        // Serial disclosure lines: buffer + cadence (P3 engine, §6.10) ride
        // into the ship report so the retail package carries the schedule
        // evidence the serial pillar requires.
        val cadenceLines = if (book.isSerialized) {
            buildList {
                add(BufferCadence.buffer(book, chapters).message)
                val cadence = BufferCadence.cadence(book, chapters)
                add(cadence.message)
                addAll(cadence.slips.take(5))
            }
        } else emptyList()

        val result = RetailPackager.packageRetail(
            RetailPackager.Input(
                bookTitle = book.title,
                author = wb.author,
                isSerial = book.isSerialized,
                chapters = imported.snapshot.chapters,
                arcs = imported.snapshot.arcs,
                guns = imported.snapshot.guns,
                cadenceLines = cadenceLines,
                workbench = imported.snapshot.publishing
            ),
            outDir
        )
        return PackageOutcome(
            result = result,
            containerFile = containerFile,
            containerBytes = containerFile.length(),
            importedSchemaVersion = imported.manifest.schemaVersion
        )
    }

    private fun slug(title: String): String =
        title.replace(Regex("[^a-zA-Z0-9.-]"), "_").ifBlank { "project" }
}
