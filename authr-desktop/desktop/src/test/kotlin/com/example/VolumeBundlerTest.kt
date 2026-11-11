package com.example

import com.example.data.model.SerialArc
import com.example.engine.publishing.Epub3Exporter
import com.example.engine.publishing.VolumeBundler
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Volume bundling (v3.0 P5, plan §13 P5 row + §6.13): a serial's canon
 * chapters group by micro-arc when assignments exist, otherwise by size;
 * every volume exports through the same EPUB 3 pipeline.
 */
class VolumeBundlerTest {

    private fun arc(id: Int, idx: Int, conflict: String) = SerialArc(
        id = id, bookId = 1, arcIndex = idx, conflict = conflict,
        phaseBounds = """{"setup":[1,3],"escalation":[4,7],"reversal":[8,11],"climax":[12,15]}"""
    )

    @Test
    fun `chapters group by their micro-arc when assigned`() {
        val arcs = listOf(arc(11, 1, "The toll bridge dispute"), arc(12, 2, "The sealed shaft"))
        val chapters = listOf(
            Epub3Exporter.Chapter(1, "One", "text"), Epub3Exporter.Chapter(2, "Two", "text"),
            Epub3Exporter.Chapter(3, "Three", "text"), Epub3Exporter.Chapter(4, "Four", "text")
        )
        val arcIds = mapOf(1 to 11, 2 to 11, 3 to 12, 4 to 12)
        val volumes = VolumeBundler.bundle(
            chapters = chapters, arcs = arcs,
            arcIdOf = { arcIds[it.number] },
            toSource = { it }, bookTitle = "Serial"
        )
        assertEquals(2, volumes.size)
        assertEquals(2, volumes[0].chapters.size)
        assertEquals("Serial — Volume 1: The toll bridge dispute", volumes[0].title)
        assertEquals("Serial — Volume 2: The sealed shaft", volumes[1].title)
        assertEquals("The sealed shaft", volumes[1].arcConflict)
    }

    @Test
    fun `unassigned chapters fall back to size grouping`() {
        val chapters = (1..15).map { Epub3Exporter.Chapter(it, "Ch $it", "text") }
        val volumes = VolumeBundler.bundle(
            chapters = chapters, arcs = emptyList(),
            arcIdOf = { null }, toSource = { it }, bookTitle = "Serial",
            maxChaptersPerVolume = 8
        )
        assertEquals(2, volumes.size)
        assertEquals(8, volumes[0].chapters.size)
        assertEquals(7, volumes[1].chapters.size)
        assertEquals(null, volumes[0].arcConflict)
    }

    @Test
    fun `arc grouping keeps reading order inside a volume`() {
        val arcs = listOf(arc(21, 1, "A"), arc(22, 2, "B"))
        // interleaved numbers but arc-ordered: 1,2 → arc1; 3,4 → arc2
        val chapters = listOf(
            Epub3Exporter.Chapter(1, "One", "1"), Epub3Exporter.Chapter(2, "Two", "2"),
            Epub3Exporter.Chapter(3, "Three", "3"), Epub3Exporter.Chapter(4, "Four", "4")
        )
        val arcIds = mapOf(1 to 21, 2 to 21, 3 to 22, 4 to 22)
        val volumes = VolumeBundler.bundle(chapters, arcs, { arcIds[it.number] }, { it }, "S")
        assertEquals(listOf(1, 2), volumes[0].chapters.map { it.number })
        assertEquals(listOf(3, 4), volumes[1].chapters.map { it.number })
    }

    @Test
    fun `exportVolumes writes one epub per volume through the shared exporter`() {
        val arcs = listOf(arc(31, 1, "First arc"), arc(32, 2, "Second arc"))
        val chapters = (1..4).map { Epub3Exporter.Chapter(it, "Ch $it", "Body $it.") }
        val arcIds = mapOf(1 to 31, 2 to 31, 3 to 32, 4 to 32)
        val volumes = VolumeBundler.bundle(chapters, arcs, { arcIds[it.number] }, { it }, "Serial")
        val out = createTempDir("volumes")
        val results = VolumeBundler.exportVolumes(
            volumes = volumes, outDir = out,
            base = Epub3Exporter.Input(title = "Serial", author = "A", chapters = volumes[0].chapters),
            identifierUuid = "vol-test", modifiedIso = "2026-11-11T00:00:00Z"
        )
        assertEquals(2, results.size)
        assertTrue(File(out, "volume_01.epub").length() > 0)
        assertTrue(File(out, "volume_02.epub").length() > 0)
        assertEquals(2, results[1].chapterCount)
        // series provenance: the parent book is the collection, the volume is its position
        assertTrue(results[0].file.name.startsWith("volume_"))
        out.deleteRecursively()
    }

    @Test
    fun `empty canon is rejected`() {
        val ex = try {
            VolumeBundler.bundle(emptyList<Epub3Exporter.Chapter>(), emptyList(), { null }, { it }, "X")
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(ex != null)
    }
}
