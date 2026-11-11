package com.example.engine.publishing

import com.example.data.model.SerialArc
import java.io.File

/**
 * Volume bundling (v3.0 P5, plan §13 P5 row; §6.13: "Volumes bundle into
 * EPUB 3 through the same pipeline as novels (section 11), so a completed
 * serial graduates to retail without rework").
 *
 * A serial's canon chapters are grouped into volumes — by micro-arc when
 * arc assignments exist (the arc is the natural retail unit: each closes
 * its question, per the §6.5 completion requirement), otherwise into
 * contiguous groups of at most [DEFAULT_MAX_CHAPTERS_PER_VOLUME] chapters.
 * Novels are a single volume — the whole book.
 *
 * Every volume exports through [Epub3Exporter] — the same pipeline, so the
 * epubcheck gate applies to each volume exactly as to the novel EPUB.
 */
object VolumeBundler {

    const val DEFAULT_MAX_CHAPTERS_PER_VOLUME = 25

    data class Volume(
        val number: Int,
        val title: String,
        /** The arc conflict that anchors the volume, when arc-based. */
        val arcConflict: String?,
        val chapters: List<Epub3Exporter.Chapter>
    )

    /**
     * Groups [chapterSources] (canon chapters in reading order) into
     * volumes. When [arcs] is non-empty and chapters carry [arcId][com.example.data.model.Chapter.arcId]
     * links, chapters group by their arc's [arcIndex][SerialArc.arcIndex];
     * otherwise they group by [maxChaptersPerVolume].
     *
     * [toSource] lifts the caller's chapter type into the exporter's
     * [Epub3Exporter.Chapter] (number/title/text) — the bundler stays
     * decoupled from the entity.
     */
    fun <T> bundle(
        chapters: List<T>,
        arcs: List<SerialArc>,
        arcIdOf: (T) -> Int?,
        toSource: (T) -> Epub3Exporter.Chapter,
        bookTitle: String,
        maxChaptersPerVolume: Int = DEFAULT_MAX_CHAPTERS_PER_VOLUME
    ): List<Volume> {
        require(chapters.isNotEmpty()) { "volume bundling needs at least one canon chapter" }
        require(maxChaptersPerVolume >= 1) { "maxChaptersPerVolume must be >= 1" }

        val arcNumbers = arcs.map { it.id to it.arcIndex }.toMap()
        val arcConflicts = arcs.map { it.id to it.conflict }.toMap()
        val useArcs = chapters.any { arcIdOf(it) != null && arcNumbers.containsKey(arcIdOf(it)) }

        val groups: List<Pair<Int?, List<T>>> = if (useArcs) {
            chapters
                .groupBy { arcIdOf(it)?.let { id -> arcNumbers[id] } }
                .toSortedMap(compareBy { it ?: Int.MAX_VALUE })
                .map { (arcIdx, chs) -> arcIdx to chs }
        } else {
            chapters.chunked(maxChaptersPerVolume).map { null to it }
        }

        return groups.mapIndexed { i, (arcIdx, chs) ->
            val number = i + 1
            val conflict = arcIdx?.let { idx ->
                arcConflicts.entries.firstOrNull { it.value != null && arcNumbers[it.key] == idx }?.value
            }
            val title = if (useArcs) {
                "${bookTitle} — Volume $number" + (conflict?.let { ": $it" } ?: "")
            } else {
                "${bookTitle} — Volume $number"
            }
            Volume(
                number = number,
                title = title,
                arcConflict = conflict,
                chapters = chs.map(toSource)
            )
        }
    }

    /** Exports every volume as its own EPUB into [outDir]. */
    fun exportVolumes(
        volumes: List<Volume>,
        outDir: File,
        base: Epub3Exporter.Input,
        identifierUuid: String? = null,
        modifiedIso: String? = null
    ): List<Epub3Exporter.Result> {
        outDir.mkdirs()
        return volumes.map { v ->
            val one = base.copy(
                title = v.title,
                seriesName = base.title, // the parent book is the series
                seriesNumber = v.number,
                chapters = v.chapters,
                identifierUuid = identifierUuid?.let { "$it-v${v.number}" },
                modifiedIso = modifiedIso
            )
            Epub3Exporter.export(one, File(outDir, "volume_%02d.epub".format(v.number)))
        }
    }
}
