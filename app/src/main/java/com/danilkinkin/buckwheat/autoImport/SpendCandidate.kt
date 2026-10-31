package com.danilkinkin.buckwheat.autoImport

data class SpendCandidate(
    // Stable id of the source item, e.g. SMS row id or notification key + text
    val fingerprint: String,
    val channel: SpendChannel,
    val sourcePackage: String?,
    val title: String?,
    val text: String,
    val timestamp: Long,
)
