package com.roundmusic.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundmusic.player.data.datastore.SettingsRepository
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.data.model.SortField
import com.roundmusic.player.data.model.SortOrder
import com.roundmusic.player.playback.PlaybackController
import com.roundmusic.player.repository.LibraryRepository
import com.roundmusic.player.repository.LibraryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SongsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** All songs on the phone, before searching. */
    val totalCount: Int = 0,
    /** Songs after searching and sorting. This is what the list shows. */
    val songs: List<Song> = emptyList(),
    val sort: SortOrder = SortOrder()
)

class SongsViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SongsUiState> =
        combine(library.observeLibrary(), query, settings.sortOrder) { state, q, sort ->
            when (state) {
                is LibraryState.Error ->
                    SongsUiState(isLoading = false, error = state.message, sort = sort)

                is LibraryState.Ready ->
                    SongsUiState(
                        isLoading = false,
                        totalCount = state.songs.size,
                        songs = filterAndSort(state.songs, q, sort),
                        sort = sort
                    )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onSortFieldSelected(field: SortField) {
        val current = uiState.value.sort
        val next = if (current.field == field) {
            current.copy(ascending = !current.ascending) // same field again: flip direction
        } else {
            SortOrder(field, field.defaultAscending)
        }
        viewModelScope.launch { settings.setSortOrder(next) }
    }

    fun rescan() {
        library.refresh()
    }

    /** Plays the tapped song. The queue becomes the list you are looking at (after search and sort). */
    fun onSongClick(song: Song) {
        val songs = uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }
        if (index >= 0) playback.playSongs(songs, index)
    }

    private fun filterAndSort(songs: List<Song>, query: String, sort: SortOrder): List<Song> {
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }

        val comparator: Comparator<Song> = when (sort.field) {
            SortField.TITLE -> compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortField.ARTIST -> compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            SortField.DATE_ADDED -> compareBy<Song> { it.dateAddedSec }
            SortField.DURATION -> compareBy<Song> { it.durationMs }
            SortField.SIZE -> compareBy<Song> { it.sizeBytes }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

        val sorted = filtered.sortedWith(comparator)
        return if (sort.ascending) sorted else sorted.asReversed()
    }
}
