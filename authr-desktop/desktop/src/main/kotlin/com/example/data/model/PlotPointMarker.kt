package com.example.data.model

data class PlotPointMarker(
    val chapterIndex: Int,
    val titleLabel: String,
    val plotSummary: String,
    var timestampMs: Long = 0L,
    var durationMs: Long = 0L
)
