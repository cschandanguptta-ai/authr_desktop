import com.example.desktopbridge.EpubCheckLocator
import com.example.engine.publishing.PrintInterior

fun main() {
    // Simulates the PACKAGED app: PrintInterior/EpubCheckLocator classes load
    // from the image's app jar, so their code-source parent is lib/app — the
    // bundled epubcheck/ and fonts/ dirs live there.
    val jar = EpubCheckLocator.epubCheckJar()
    println("epubcheck: ${jar ?: "NOT FOUND"}")
    check(jar != null && jar.contains("lib/app/epubcheck")) { "locator must hit the bundled dist" }
    val font = PrintInterior.resolveFontPath("", bold = false)
    println("font: $font")
    check(font.contains("lib/app/fonts")) { "resolver must hit the bundled face" }
    println("BUNDLE LAYOUT OK")
}
