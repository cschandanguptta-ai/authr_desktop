import com.example.engine.publishing.Epub3Exporter
import com.example.engine.publishing.PrintInterior
import com.example.desktopbridge.EpubCheckLocator
import java.io.File

/** ADR-0010: where the ~7 s novel package actually goes. */
fun timed(name: String, block: () -> Any): Any {
    val t0 = System.nanoTime()
    val r = block()
    println("$name: ${(System.nanoTime() - t0) / 1_000_000} ms")
    return r
}

fun main() {
    val out = File("/tmp/d15breakdown").apply { mkdirs() }
    val chapters = (1..4).map { n ->
        Epub3Exporter.Chapter(n, "Chapter $n",
            (1..60).joinToString(" ") { "word$it chapter$n the text keeps the render honest and the page count real." })
    }
    val epub = timed("Epub3Exporter.export (4 ch)") {
        Epub3Exporter.export(Epub3Exporter.Input(
            title = "Probe", author = "P", chapters = chapters), File(out, "probe.epub"))
    } as Epub3Exporter.Result
    timed("PrintInterior.render (4 ch)") {
        PrintInterior.render(PrintInterior.Input(title = "Probe",
            chapters = chapters.map { PrintInterior.ChapterSource(it.number, it.title, it.bodyText) }), File(out, "interior.pdf"))
    }
    val jar = EpubCheckLocator.epubCheckJar() ?: error("no epubcheck")
    repeat(2) { i ->
        timed("epubcheck run #$i (JVM spawn + check)") {
            val p = ProcessBuilder(listOf(EpubCheckLocator.javaBin(), "-jar", jar, epub.file.absolutePath))
                .redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor()
        }
    }
}
