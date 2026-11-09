package com.example.managers

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.model.Story
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ImageValidationResult(
    val isValid: Boolean,
    val width: Int,
    val height: Int,
    val aspectRatio: Float,
    val warningMessage: String?
)

fun Story.getChapters(): List<String> {
    return listOf(part1, part2, part3, part4, part5).filter { it.isNotEmpty() }
}

object EpubManager {

    /**
     * Sanitizes strings for safe inclusion in XML elements and attributes.
     */
    fun escapeXml(text: String): String {
        if (text.isEmpty()) return ""
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Copy picked cover image to the local stories assets directory
     */
    fun saveCoverImage(context: Context, storyId: Int, uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val coverFile = File(context.filesDir, "story_${storyId}_cover.jpg")
            coverFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            Log.d("EpubManager", "Successfully saved cover image for story $storyId to ${coverFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("EpubManager", "Failed to save cover image", e)
            false
        }
    }

    /**
     * Export complete book package as a compressed .zip archive containing TXT, Markdown, Story Bible, and Outline.
     */
    fun exportBookToZipPackage(context: Context, bookTitle: String, manuscript: String, bible: String, outline: String): String? {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val studioDir = File(downloads, "Weaver_Studio")
            studioDir.mkdirs()
            val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val zipFile = File(studioDir, "${safeTitle}_Package.zip")
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("Manuscript.txt"))
                zip.write(manuscript.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("Manuscript.md"))
                zip.write("# $bookTitle\n\n$manuscript".toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("StoryBible.txt"))
                zip.write(bible.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("Outline.txt"))
                zip.write(outline.toByteArray())
                zip.closeEntry()
            }
            zipFile.absolutePath
        } catch (e: Exception) {
            Log.e("EpubManager", "Failed to export ZIP package", e)
            null
        }
    }

    /**
     * Validate the saved cover image's resolution and aspect ratio
     */
    fun validateCoverImage(context: Context, storyId: Int): ImageValidationResult {
        val coverFile = File(context.filesDir, "story_${storyId}_cover.jpg")
        if (!coverFile.exists()) {
            return ImageValidationResult(
                isValid = false,
                width = 0,
                height = 0,
                aspectRatio = 0f,
                warningMessage = "No cover image file uploaded. The eBook will use a dynamically generated typography cover."
            )
        }
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(coverFile.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) {
                ImageValidationResult(
                    isValid = false,
                    width = 0,
                    height = 0,
                    aspectRatio = 0f,
                    warningMessage = "Invalid image format or corrupt file."
                )
            } else {
                val ratio = height.toFloat() / width
                var warning: String? = null
                if (width < 1400 || height < 2100) {
                    warning = "Low resolution: Recommended cover resolution is at least 1400x2100 px for professional platforms (current: ${width}x${height} px)."
                } else if (ratio < 1.4f || ratio > 1.7f) {
                    warning = "Suboptimal aspect ratio: Recommended aspect ratio is 1:1.5 to 1:1.6 (current: 1:${String.format(Locale.US, "%.2f", ratio)}). Cover may scale awkwardly on some eReaders."
                }
                ImageValidationResult(
                    isValid = true,
                    width = width,
                    height = height,
                    aspectRatio = ratio,
                    warningMessage = warning
                )
            }
        } catch (e: Exception) {
            ImageValidationResult(
                isValid = false,
                width = 0,
                height = 0,
                aspectRatio = 0f,
                warningMessage = "Error validating cover image: ${e.message}"
            )
        }
    }

    /**
     * Generate an ultra-detailed cover art concept for Midjourney/ChatGPT/Gemini
     */
    fun generateCoverPrompt(story: Story): String {
        val title = story.title
        val genres = story.genres.ifEmpty { "Fiction" }
        val hero = story.heroName.ifEmpty { "a protagonist" }
        val hook = story.hook.ifEmpty { "an epic adventure" }
        val extras = story.extras.ifEmpty { "" }
        val mood = when {
            genres.contains("Thriller", true) || genres.contains("Horror", true) -> "dark, mysterious, suspenseful, tense"
            genres.contains("Fantasy", true) || genres.contains("Sci-Fi", true) -> "epic, mystical, wonderous, celestial"
            genres.contains("Romance", true) -> "emotional, romantic, intimate, soft, warm"
            else -> "compelling, cinematic, dramatic"
        }
        val artStyle = when {
            genres.contains("Fantasy", true) -> "Dramatic digital fantasy illustration, high-fantasy style, highly detailed"
            genres.contains("Sci-Fi", true) -> "Futuristic cinematic concept art, sci-fi cyberpunk aesthetic, sleek rendering"
            genres.contains("Thriller", true) -> "Chiaroscuro film noir, dramatic shadows, mysterious realism"
            genres.contains("Romance", true) -> "Soft focus impressionistic digital painting, warm pastel color palette"
            else -> "Cinematic oil painting style, masterpiece digital art, volumetric lighting"
        }
        
        return """
            EBook Cover Art Concept for "$title" (Genre: $genres)
            --------------------------------------------------
            Artistic Style: $artStyle.
            Subject & Character: A highly detailed depiction of $hero, matching the central premise: "$hook".
            Atmosphere & Mood: $mood, evocative, captivating.
            Color Palette: Masterful lighting with contrasting tones, color-graded to match the thematic depth of the story.
            Composition & Camera Angle: Centered single-focus composition with generous negative space at the top and bottom for title typography placement. High contrast between subject and background.
            Visual Symbolism: Incorporating elements representing: $extras.
            Rendering Instructions: Extremely detailed rendering, volumetric lighting, photorealistic textures, 8k resolution concept art. No watermarks, no embedded text, no logos, no typography. Perfect aspect ratio for eBook cover (2:3 aspect ratio).
        """.trimIndent()
    }

    /**
     * Main EPUB 3 generation pipeline, returning whether export was successful
     */
    fun exportToEpub(
        context: Context, 
        story: Story,
        authorName: String = "",
        authorPenName: String = "",
        authorBio: String = "",
        authorPublisher: String = "",
        authorWebsite: String = "",
        authorSocial: String = "",
        subtitle: String = "",
        seriesTitle: String = "",
        volumeNumber: String = "",
        edition: String = "First Edition",
        isbn: String = "",
        dedication: String = "",
        acknowledgements: String = "",
        audioFiles: List<File>? = null
    ): Boolean {
        val cleanTitle = story.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "${cleanTitle}_eBook.epub"
        var savedSuccess = false
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/epub+zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        writeEpubToStream(
                            context = context,
                            story = story,
                            out = out,
                            authorName = authorName,
                            authorPenName = authorPenName,
                            authorBio = authorBio,
                            authorPublisher = authorPublisher,
                            authorWebsite = authorWebsite,
                            authorSocial = authorSocial,
                            subtitle = subtitle,
                            seriesTitle = seriesTitle,
                            volumeNumber = volumeNumber,
                            edition = edition,
                            isbn = isbn,
                            dedication = dedication,
                            acknowledgements = acknowledgements
                        )
                    }
                    savedSuccess = true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                destFile.outputStream().use { out ->
                    writeEpubToStream(
                        context = context,
                        story = story,
                        out = out,
                        authorName = authorName,
                        authorPenName = authorPenName,
                        authorBio = authorBio,
                        authorPublisher = authorPublisher,
                        authorWebsite = authorWebsite,
                        authorSocial = authorSocial,
                        subtitle = subtitle,
                        seriesTitle = seriesTitle,
                        volumeNumber = volumeNumber,
                        edition = edition,
                        isbn = isbn,
                        dedication = dedication,
                        acknowledgements = acknowledgements
                    )
                }
                savedSuccess = true
            }
        } catch (e: Exception) {
            Log.e("EpubManager", "Failed exporting EPUB", e)
            savedSuccess = false
        }
        return savedSuccess
    }

    fun exportToEpubFile(
        context: Context,
        story: Story,
        destFile: File,
        authorName: String = "",
        authorPenName: String = "",
        authorBio: String = "",
        authorPublisher: String = "",
        authorWebsite: String = "",
        authorSocial: String = "",
        subtitle: String = "",
        seriesTitle: String = "",
        volumeNumber: String = "",
        edition: String = "First Edition",
        isbn: String = "",
        dedication: String = "",
        acknowledgements: String = "",
        audioFiles: List<File>? = null
    ): Boolean {
        return try {
            destFile.outputStream().use { out ->
                writeEpubToStream(
                    context, story, out, authorName, authorPenName, authorBio, authorPublisher,
                    authorWebsite, authorSocial, subtitle, seriesTitle, volumeNumber, edition,
                    isbn, dedication, acknowledgements
                )
            }
            true
        } catch (e: Exception) {
            Log.e("EpubManager", "Failed to write EPUB to file", e)
            false
        }
    }

    fun writeEpubToStream(
        context: Context,
        story: Story,
        out: OutputStream,
        authorName: String,
        authorPenName: String,
        authorBio: String,
        authorPublisher: String,
        authorWebsite: String,
        authorSocial: String,
        subtitle: String,
        seriesTitle: String,
        volumeNumber: String,
        edition: String,
        isbn: String,
        dedication: String,
        acknowledgements: String,
        audioFiles: List<File>? = null
    ) {
        val zos = ZipOutputStream(out)
        
        // 1. mimetype (Must be first, completely uncompressed)
        val mimeEntry = ZipEntry("mimetype").apply { method = ZipEntry.STORED }
        val mimeBytes = "application/epub+zip".toByteArray()
        mimeEntry.size = mimeBytes.size.toLong()
        mimeEntry.crc = getCrc(mimeBytes)
        zos.putNextEntry(mimeEntry)
        zos.write(mimeBytes)
        zos.closeEntry()

        // 2. META-INF/container.xml
        zos.putNextEntry(ZipEntry("META-INF/container.xml"))
        val containerXml = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
</container>""".trimIndent()
        zos.write(containerXml.toByteArray())
        zos.closeEntry()

        // Setup dynamic properties
        val primaryAuthor = authorPenName.ifEmpty { authorName.ifEmpty { "Unknown Author" } }
        val publisherVal = authorPublisher.ifEmpty { "Audio Novel Studio" }
        val finalSubtitle = subtitle.ifEmpty { story.genres + " Novel" }
        val currentYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val bookUuid = "urn:uuid:" + UUID.nameUUIDFromBytes(story.title.toByteArray()).toString()
        val coverFile = File(context.filesDir, "story_${story.id}_cover.jpg")
        val hasCoverFile = coverFile.exists()

        // 3. Write CSS Stylesheet
        zos.putNextEntry(ZipEntry("OEBPS/css/stylesheet.css"))
        val cssContent = """
            body {
                font-family: "Georgia", "Times New Roman", serif;
                line-height: 1.6;
                margin: 5% 8%;
                color: #111111;
                background-color: #FFFFFF;
            }
            h1, h2, h3 {
                font-family: "Helvetica Neue", "Arial", sans-serif;
                text-align: center;
                color: #2c3e50;
                margin-top: 1.5em;
                margin-bottom: 0.8em;
            }
            h1 {
                font-size: 1.8em;
                border-bottom: 1px solid #ecf0f1;
                padding-bottom: 0.3em;
            }
            h2 {
                font-size: 1.4em;
            }
            p {
                text-indent: 1.5em;
                margin: 0;
                text-align: justify;
            }
            p.first-paragraph {
                text-indent: 0;
                margin-top: 1.5em;
            }
            p.first-paragraph:first-letter {
                font-size: 3.2em;
                float: left;
                margin: 0.1em 0.1em 0 0;
                line-height: 0.85;
                font-weight: bold;
                color: #2c3e50;
                font-family: "Georgia", serif;
            }
            .scene-break {
                text-align: center;
                margin: 2em 0;
                font-size: 1.4em;
                letter-spacing: 0.5em;
                color: #7f8c8d;
            }
            .center-text {
                text-align: center;
                text-indent: 0;
            }
            .dedication {
                font-style: italic;
                text-align: center;
                margin-top: 5em;
                line-height: 1.8;
            }
            .copyright-info {
                font-size: 0.9em;
                color: #34495e;
                line-height: 1.8;
                text-indent: 0;
                margin-bottom: 1em;
            }
            .about-author-section {
                margin-top: 2em;
            }
            .cover-img {
                display: block;
                max-width: 100%;
                max-height: 100%;
                margin: 0 auto;
            }
        """.trimIndent()
        zos.write(cssContent.toByteArray())
        zos.closeEntry()

        // 4. Write cover image if it exists
        if (hasCoverFile) {
            zos.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
            coverFile.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }

        // 5. OEBPS/content.opf (Manifest and Spine)
        zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
        val chapters = story.getChapters()
        val manifestItems = StringBuilder()
        val spineItems = StringBuilder()

        chapters.forEachIndexed { idx, _ ->
            val hasAudio = audioFiles != null && audioFiles.size > idx
            val mediaOverlay = if (hasAudio) " media-overlay=\"smil-chapter${idx+1}\"" else ""
            
            manifestItems.append("""<item id="chapter${idx+1}" href="chapter${idx+1}.xhtml" media-type="application/xhtml+xml"$mediaOverlay/>""").append("\n        ")
            
            if (hasAudio) {
                val audioFile = audioFiles!![idx]
                val ext = audioFile.extension.ifEmpty { "mp3" }
                val mediaType = if (ext.lowercase() == "wav") "audio/wav" else "audio/mpeg"
                manifestItems.append("""<item id="smil-chapter${idx+1}" href="smil/chapter${idx+1}.smil" media-type="application/smil+xml"/>""").append("\n        ")
                manifestItems.append("""<item id="audio-chapter${idx+1}" href="audio/chapter${idx+1}.$ext" media-type="$mediaType"/>""").append("\n        ")
            }
            
            spineItems.append("""<itemref idref="chapter${idx+1}"/>""").append("\n        ")
        }

        val coverImageManifestItem = if (hasCoverFile) {
            """<item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""
        } else ""

        val contentOpf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:title>${story.title}</dc:title>
        <dc:creator id="creator">${primaryAuthor}</dc:creator>
        <dc:language>en</dc:language>
        <dc:identifier id="BookId">${bookUuid}</dc:identifier>
        <dc:publisher>${publisherVal}</dc:publisher>
        <dc:date>${isoDate}</dc:date>
        <dc:description>${story.metaDesc.ifEmpty { story.hook }}</dc:description>
        <dc:subject>${story.genres}</dc:subject>
        <meta property="dcterms:modified">${isoDate}</meta>
        ${if (hasCoverFile) """<meta name="cover" content="cover-image"/>""" else ""}
    </metadata>
    <manifest>
        <item id="stylesheet" href="css/stylesheet.css" media-type="text/css"/>
        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
        <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>
        <item id="title-page" href="title_page.xhtml" media-type="application/xhtml+xml"/>
        <item id="copyright-page" href="copyright.xhtml" media-type="application/xhtml+xml"/>
        <item id="dedication-page" href="dedication.xhtml" media-type="application/xhtml+xml"/>
        $manifestItems
        <item id="acknowledgements-page" href="acknowledgements.xhtml" media-type="application/xhtml+xml"/>
        <item id="about-author-page" href="about_author.xhtml" media-type="application/xhtml+xml"/>
        <item id="promo-page" href="promo.xhtml" media-type="application/xhtml+xml"/>
        $coverImageManifestItem
    </manifest>
    <spine toc="ncx">
        <itemref idref="cover-page"/>
        <itemref idref="title-page"/>
        <itemref idref="copyright-page"/>
        <itemref idref="dedication-page"/>
        $spineItems
        <itemref idref="acknowledgements-page"/>
        <itemref idref="about-author-page"/>
        <itemref idref="promo-page"/>
    </spine>
</package>""".trimIndent()
        zos.write(contentOpf.toByteArray())
        zos.closeEntry()

        // 6. OEBPS/nav.xhtml (MANDATORY EPUB 3 Navigation Document)
        zos.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
        val navChapters = StringBuilder()
        chapters.forEachIndexed { idx, _ ->
            navChapters.append("""<li><a href="chapter${idx+1}.xhtml">Chapter ${idx+1}</a></li>""").append("\n            ")
        }

        val navXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en" xml:lang="en">
<head>
    <title>Navigation</title>
    <meta charset="utf-8" />
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <nav epub:type="toc" id="toc">
        <h1>Table of Contents</h1>
        <ol>
            <li><a href="cover.xhtml">Cover</a></li>
            <li><a href="title_page.xhtml">Title Page</a></li>
            <li><a href="copyright.xhtml">Copyright</a></li>
            <li><a href="dedication.xhtml">Dedication</a></li>
            $navChapters
            <li><a href="acknowledgements.xhtml">Acknowledgements</a></li>
            <li><a href="about_author.xhtml">About the Author</a></li>
            <li><a href="promo.xhtml">Other Works</a></li>
        </ol>
    </nav>
</body>
</html>""".trimIndent()
        zos.write(navXhtml.toByteArray())
        zos.closeEntry()

        // 7. OEBPS/toc.ncx (EPUB 2 backward compatibility)
        zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
        val navPoints = StringBuilder()
        navPoints.append("""
        <navPoint id="navPoint-cover" playOrder="1">
            <navLabel><text>Cover</text></navLabel>
            <content src="cover.xhtml"/>
        </navPoint>
        <navPoint id="navPoint-title" playOrder="2">
            <navLabel><text>Title Page</text></navLabel>
            <content src="title_page.xhtml"/>
        </navPoint>
        <navPoint id="navPoint-copyright" playOrder="3">
            <navLabel><text>Copyright</text></navLabel>
            <content src="copyright.xhtml"/>
        </navPoint>
        <navPoint id="navPoint-dedication" playOrder="4">
            <navLabel><text>Dedication</text></navLabel>
            <content src="dedication.xhtml"/>
        </navPoint>""")

        var playOrder = 5
        chapters.forEachIndexed { idx, _ ->
            navPoints.append("""
        <navPoint id="navPoint-chapter-${idx+1}" playOrder="${playOrder++}">
            <navLabel><text>Chapter ${idx+1}</text></navLabel>
            <content src="chapter${idx+1}.xhtml"/>
        </navPoint>""")
        }

        navPoints.append("""
        <navPoint id="navPoint-acknowledgements" playOrder="${playOrder++}">
            <navLabel><text>Acknowledgements</text></navLabel>
            <content src="acknowledgements.xhtml"/>
        </navPoint>
        <navPoint id="navPoint-about" playOrder="${playOrder++}">
            <navLabel><text>About the Author</text></navLabel>
            <content src="about_author.xhtml"/>
        </navPoint>
        <navPoint id="navPoint-promo" playOrder="${playOrder}">
            <navLabel><text>Other Works</text></navLabel>
            <content src="promo.xhtml"/>
        </navPoint>""")

        val tocNcx = """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
    <head>
        <meta name="dtb:uid" content="${bookUuid}"/>
    </head>
    <docTitle><text>${story.title}</text></docTitle>
    <navMap>
        $navPoints
    </navMap>
</ncx>""".trimIndent()
        zos.write(tocNcx.toByteArray())
        zos.closeEntry()

        // 8. OEBPS/cover.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/cover.xhtml"))
        val coverContent = if (hasCoverFile) {
            """<img class="cover-img" src="images/cover.jpg" alt="Cover Image"/>"""
        } else {
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 900" width="100%" height="100%">
                <rect width="600" height="900" fill="#1e272e"/>
                <rect x="20" y="20" width="560" height="860" fill="none" stroke="#d2dae2" stroke-width="2"/>
                <text x="300" y="250" font-family="'Helvetica Neue', Arial, sans-serif" font-size="36" font-weight="bold" fill="#ffffff" text-anchor="middle">${story.title}</text>
                <text x="300" y="310" font-family="'Georgia', serif" font-size="20" font-style="italic" fill="#ffdd59" text-anchor="middle">${finalSubtitle}</text>
                <line x1="150" y1="450" x2="450" y2="450" stroke="#ffdd59" stroke-width="2"/>
                <text x="300" y="600" font-family="'Helvetica Neue', Arial, sans-serif" font-size="24" fill="#ffffff" text-anchor="middle">BY</text>
                <text x="300" y="650" font-family="'Georgia', serif" font-size="28" font-weight="bold" fill="#ffdd59" text-anchor="middle">${primaryAuthor}</text>
                <text x="300" y="800" font-family="'Helvetica Neue', Arial, sans-serif" font-size="16" fill="#808e9b" text-anchor="middle">${publisherVal}</text>
            </svg>
            """.trimIndent()
        }

        val coverXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Cover</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
    <style type="text/css">
        @page { padding: 0; margin: 0; }
        body { padding: 0; margin: 0; text-align: center; background-color: #111111; }
        .cover-container { height: 100%; width: 100%; display: flex; align-items: center; justify-content: center; }
    </style>
</head>
<body>
    <div class="cover-container">
        $coverContent
    </div>
</body>
</html>""".trimIndent()
        zos.write(coverXhtml.toByteArray())
        zos.closeEntry()

        // 9. OEBPS/title_page.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/title_page.xhtml"))
        val titlePageHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Title Page</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <div style="text-align: center; margin-top: 5em;">
        <h1 style="font-size: 2.5em; border: none; margin-bottom: 0.2em;">${story.title}</h1>
        <h3 style="font-style: italic; margin-top: 0; color: #555;">$finalSubtitle</h3>
        ${if (seriesTitle.isNotEmpty()) """<h4 style="margin-top: 1em; color: #7f8c8d;">${seriesTitle}${if (volumeNumber.isNotEmpty()) " (Vol. $volumeNumber)" else ""}</h4>""" else ""}
        
        <div style="margin-top: 8em; font-size: 1.2em;">
            <p class="center-text">By</p>
            <h2 style="margin-top: 0.5em; font-size: 1.8em;">$primaryAuthor</h2>
        </div>
        
        <div style="margin-top: 10em; font-size: 0.9em; color: #7f8c8d;">
            <p class="center-text">$publisherVal</p>
        </div>
    </div>
</body>
</html>""".trimIndent()
        zos.write(titlePageHtml.toByteArray())
        zos.closeEntry()

        // 10. OEBPS/copyright.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/copyright.xhtml"))
        val copyrightHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Copyright</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <h1>Copyright Notice</h1>
    <div class="copyright-info" style="margin-top: 3em;">
        <p class="center-text"><strong>${story.title}</strong></p>
        <p class="center-text">By $primaryAuthor</p>
        <p class="center-text" style="margin-top: 2em;">Copyright © $currentYear by $primaryAuthor</p>
        <p class="center-text">All rights reserved.</p>
        
        <div style="margin-top: 3em; border-top: 1px solid #eee; padding-top: 2em; font-size: 0.85em;">
            <p>No part of this publication may be reproduced, distributed, or transmitted in any form or by any means, including photocopying, recording, or other electronic or mechanical methods, without the prior written permission of the publisher, except in the case of brief quotations embodied in critical reviews and certain other noncommercial uses permitted by copyright law.</p>
            <p style="margin-top: 1.5em;"><strong>Publisher:</strong> $publisherVal</p>
            <p style="margin-top: 0.5em;"><strong>Edition:</strong> $edition</p>
            <p style="margin-top: 0.5em;"><strong>Publication Date:</strong> ${SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date())}</p>
            <p style="margin-top: 0.5em;"><strong>ISBN:</strong> ${isbn.ifEmpty { "978-0-000000-00-0 (eBook Placeholder)" }}</p>
        </div>
    </div>
</body>
</html>""".trimIndent()
        zos.write(copyrightHtml.toByteArray())
        zos.closeEntry()

        // 11. OEBPS/dedication.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/dedication.xhtml"))
        val finalDedication = dedication.ifEmpty { "Dedicated to dreamers and storytellers everywhere, whose worlds inspire our own." }
        val dedicationHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Dedication</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <div class="dedication">
        <p>$finalDedication</p>
    </div>
</body>
</html>""".trimIndent()
        zos.write(dedicationHtml.toByteArray())
        zos.closeEntry()

        // 12. Chapters & Media Overlays
        chapters.forEachIndexed { idx, text ->
            val hasAudio = audioFiles != null && audioFiles.size > idx
            var currentAudioExt = "mp3"
            
            if (hasAudio) {
                val audioFile = audioFiles!![idx]
                currentAudioExt = audioFile.extension.ifEmpty { "mp3" }
                
                // Write Audio File
                zos.putNextEntry(ZipEntry("OEBPS/audio/chapter${idx+1}.$currentAudioExt"))
                try {
                    audioFile.inputStream().use { it.copyTo(zos) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                zos.closeEntry()
                
                // Write SMIL File
                zos.putNextEntry(ZipEntry("OEBPS/smil/chapter${idx+1}.smil"))
                val smilHtml = """<?xml version="1.0" encoding="UTF-8"?>
<smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
    <body>
        <seq id="seq1" epub:textref="../chapter${idx+1}.xhtml">
            <par id="par1">
                <text src="../chapter${idx+1}.xhtml#chapter-content"/>
                <audio src="../audio/chapter${idx+1}.$currentAudioExt"/>
            </par>
        </seq>
    </body>
</smil>""".trimIndent()
                zos.write(smilHtml.toByteArray())
                zos.closeEntry()
            }

            zos.putNextEntry(ZipEntry("OEBPS/chapter${idx+1}.xhtml"))
            val paragraphs = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val formattedParagraphs = paragraphs.mapIndexed { pIdx, pText ->
                when {
                    pText == "***" || pText == "---" || pText == "###" -> {
                        """<div class="scene-break">***</div>"""
                    }
                    pIdx == 0 -> {
                        """<p class="first-paragraph">${pText.replace("<", "&lt;").replace(">", "&gt;")}</p>"""
                    }
                    else -> {
                        """<p>${pText.replace("<", "&lt;").replace(">", "&gt;")}</p>"""
                    }
                }
            }.joinToString("\n")

            val chapterHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Chapter ${idx+1}</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body id="chapter-content">
    <h1>Chapter ${idx+1}</h1>
    $formattedParagraphs
</body>
</html>""".trimIndent()
            zos.write(chapterHtml.toByteArray())
            zos.closeEntry()
        }

        // 13. OEBPS/acknowledgements.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/acknowledgements.xhtml"))
        val finalAcknowledgements = acknowledgements.ifEmpty { "Special thanks to the creative minds behind the Audio Novel Studio platform, whose tools and resources made this publication-ready creation possible. To our families, friends, and early readers: thank you for your support." }
        val acknowledgementsHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Acknowledgements</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <h1>Acknowledgements</h1>
    <div style="margin-top: 2em; line-height: 1.8;">
        <p>$finalAcknowledgements</p>
    </div>
</body>
</html>""".trimIndent()
        zos.write(acknowledgementsHtml.toByteArray())
        zos.closeEntry()

        // 14. OEBPS/about_author.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/about_author.xhtml"))
        val finalBio = authorBio.ifEmpty { "A visionary writer who explores characters and concepts in depth, weaving beautiful tales of imagination. This book is a testament to the pursuit of high-fidelity storytelling, blended with modern creative technology." }
        val finalWebsite = authorWebsite.ifEmpty { "https://audionovelstudio.com" }
        val finalSocial = authorSocial.ifEmpty { "Follow us on Twitter, Substack, and Instagram." }
        val aboutAuthorHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>About the Author</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <h1>About the Author</h1>
    <div class="about-author-section">
        <p>$finalBio</p>
        <div style="margin-top: 3em; border-top: 1px solid #eee; padding-top: 1.5em; font-size: 0.9em;">
            <p style="text-indent: 0;"><strong>Connect with the Author:</strong></p>
            <p style="text-indent: 0; margin-top: 0.5em;">Website: <a href="$finalWebsite">$finalWebsite</a></p>
            <p style="text-indent: 0; margin-top: 0.5em;">Socials: $finalSocial</p>
        </div>
    </div>
</body>
</html>""".trimIndent()
        zos.write(aboutAuthorHtml.toByteArray())
        zos.closeEntry()

        // 15. OEBPS/promo.xhtml
        zos.putNextEntry(ZipEntry("OEBPS/promo.xhtml"))
        val promoHtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
<head>
    <title>Other Works</title>
    <link rel="stylesheet" href="css/stylesheet.css" type="text/css"/>
</head>
<body>
    <h1>Other Works by the Author</h1>
    <p class="center-text" style="margin-top: 2em; font-style: italic;">Discover more stories and join our reader community!</p>
    
    <div style="margin-top: 3em; border: 1px solid #ecf0f1; padding: 2em; text-align: center; background-color: #fcfcfc;">
        <h2 style="margin-top: 0; color: #2c3e50;">Join the VIP Newsletter</h2>
        <p style="text-indent: 0; margin-bottom: 1.5em;">Be the first to hear about new releases, exclusive previews, and behind-the-scenes content direct from the author.</p>
        <p style="text-indent: 0;"><a href="$finalWebsite" style="background-color: #2c3e50; color: #ffffff; padding: 0.8em 1.5em; text-decoration: none; font-weight: bold; border-radius: 4px;">Join the Newsletter</a></p>
    </div>
</body>
</html>""".trimIndent()
        zos.write(promoHtml.toByteArray())
        zos.closeEntry()

        zos.finish()
    }

    private fun getCrc(bytes: ByteArray): Long {
        val crc32 = java.util.zip.CRC32()
        crc32.update(bytes)
        return crc32.value
    }
}
