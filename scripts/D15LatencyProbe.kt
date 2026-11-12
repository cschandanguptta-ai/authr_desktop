import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.container.AuthrContainer
import com.example.engine.publishing.RetailPackager
import com.example.engine.publishing.KdpWorkbench
import java.io.File

/** ADR-0010 probe: the one-action retail pipeline's wall clock, measured. */
fun timed(name: String, block: () -> Any): Any {
    val t0 = System.nanoTime()
    val r = block()
    val ms = (System.nanoTime() - t0) / 1_000_000
    println("$name: ${ms} ms")
    return r
}

fun main() {
    val outRoot = File("/tmp/d15lat").apply { mkdirs() }

    // 4-chapter novel through the container round trip + full one action
    val book = Book(id = 7, title = "Latency Probe Novel", genre = "Test",
        synopsis = "A short probe novel.", outline = "Four chapters.")
    val chapters = (1..4).map { n ->
        Chapter(bookId = 7, chapterNumber = n, title = "Chapter $n",
            content = (1..60).joinToString(" ") { "word$it chapter$n the text keeps the render honest and the page count real." },
            summary = "s", status = "Completed")
    }
    val wb = KdpWorkbench(title = book.title, author = "Probe", description = book.synopsis)
    timed("container export+import (novel)") {
        val cf = File(outRoot, "novel.authr")
        AuthrContainer.export(AuthrContainer.ProjectSnapshot(book, chapters, emptyList(), publishing = wb), cf)
        AuthrContainer.import(cf).snapshot
    }
    val snapshot = File(outRoot, "novel.authr").let { AuthrContainer.import(it).snapshot }
    val novelRes = timed("packageRetail novel (4 ch, epubcheck + print + report)") {
        RetailPackager.packageRetail(
            RetailPackager.Input(bookTitle = book.title, author = "Probe", isSerial = false,
                chapters = snapshot.chapters, workbench = snapshot.publishing),
            File(outRoot, "novel")
        )
    } as RetailPackager.Result
    println("  novel pages=${novelRes.printResult?.pageCount}, clean=${novelRes.clean}")

    // 15-chapter serial: 2 volumes
    val sbook = Book(id = 8, title = "Latency Probe Serial", genre = "Test",
        synopsis = "Serial probe.", outline = "Fifteen.", isSerialized = true)
    val sch = (1..15).map { n ->
        Chapter(bookId = 8, chapterNumber = n, title = "Chapter $n",
            content = (1..60).joinToString(" ") { "word$it serial chapter$n probe text for volume bundling and print." },
            summary = "s", status = "Completed", arcId = if (n <= 7) 11 else 12)
    }
    val arcs = listOf(
        com.example.data.model.SerialArc(id = 11, bookId = 8, arcIndex = 1, conflict = "one", phaseBounds = "{}"),
        com.example.data.model.SerialArc(id = 12, bookId = 8, arcIndex = 2, conflict = "two", phaseBounds = "{}")
    )
    val scf = File(outRoot, "serial.authr")
    AuthrContainer.export(AuthrContainer.ProjectSnapshot(sbook, sch, emptyList(), arcs = arcs,
        publishing = KdpWorkbench(title = sbook.title)), scf)
    val ssnap = AuthrContainer.import(scf).snapshot
    val serialRes = timed("packageRetail serial (15 ch, 2 volumes, epubcheck x2)") {
        RetailPackager.packageRetail(
            RetailPackager.Input(bookTitle = sbook.title, author = "Probe", isSerial = true,
                chapters = ssnap.chapters, arcs = ssnap.arcs, workbench = ssnap.publishing),
            File(outRoot, "serial")
        )
    } as RetailPackager.Result
    println("  serial volumes=${serialRes.epubResults.size}, pages=${serialRes.printResult?.pageCount}, clean=${serialRes.clean}")
}
