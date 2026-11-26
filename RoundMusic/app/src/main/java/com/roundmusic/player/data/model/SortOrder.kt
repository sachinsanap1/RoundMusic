package com.roundmusic.player.data.model

/** What the song list can be sorted by. [defaultAscending] is used the first time you pick it. */
enum class SortField(val label: String, val defaultAscending: Boolean) {
    TITLE("Name", true),
    ARTIST("Artist", true),
    DATE_ADDED("Date added", false),
    DURATION("Duration", false),
    SIZE("File size", false)
}

data class SortOrder(
    val field: SortField = SortField.TITLE,
    val ascending: Boolean = true
)
