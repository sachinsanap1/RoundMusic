package com.roundmusic.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundmusic.player.data.db.PlayStatsEntity
import com.roundmusic.player.data.model.AlbumInfo
import com.roundmusic.player.data.model.ArtistInfo
import com.roundmusic.player.data.model.PlaylistSummary
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.playback.PlaybackController
import com.roundmusic.player.repository.FavoritesRepository
import com.roundmusic.player.repository.LibraryRepository
import com.roundmusic.player.repository.LibraryState
import com.roundmusic.player.repository.PlaylistRepository
import com.roundmusic.player.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val SECTION_LIMIT = 30

/**
 * Everything the Home and Library screens need: albums, artists, playlists, favorites, and the
 * smart lists (Recently added / Recently played / Most played). All of it is built from real data:
 * the songs on the phone plus what is saved in the database.
 * One instance is shared by all main screens.
 */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val favorites: FavoritesRepository,
    private val playlistRepo: PlaylistRepository,
    private val stats: StatsRepository,
    private val playback: PlaybackController
) : ViewModel() {

    private val eagerlyForUi = SharingStarted.WhileSubscribed(5_000)

    // ------------------------------------------------------------------ songs

    /** null = still scanning. */
    val libraryState: StateFlow<LibraryState?> =
        library.observeLibrary().map<LibraryState, LibraryState?> { it }
            .stateIn(viewModelScope, eagerlyForUi, null)

    val songs: StateFlow<List<Song>> =
        library.observeLibrary()
            .map { (it as? LibraryState.Ready)?.songs.orEmpty() }
            .stateIn(viewModelScope, eagerlyForUi, emptyList())

    private val songsById: StateFlow<Map<Long, Song>> =
        songs.map { list -> list.associateBy { it.id } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, eagerlyForUi, emptyMap())

    // -------------------------------------------------------------- favorites

    private val favoriteIdList: StateFlow<List<Long>> =
        favorites.favoriteIds.stateIn(viewModelScope, eagerlyForUi, emptyList())

    val favoriteIds: StateFlow<Set<Long>> =
        favoriteIdList.map { it.toSet() }.stateIn(viewModelScope, eagerlyForUi, emptySet())

    /** "Liked Music": favorites that still exist on the phone, newest first. */
    val favoriteSongs: StateFlow<List<Song>> =
        combine(favoriteIdList, songsById) { ids, byId -> ids.mapNotNull { byId[it] } }
            .stateIn(viewModelScope, eagerlyForUi, emptyList())

    // -------------------------------------------------------------- playlists

    /** null = still loading. */
    val playlists: StateFlow<List<PlaylistSummary>?> =
        combine(playlistRepo.observePlaylists(), playlistRepo.observeAllEntries(), songsById) { lists, entries, byId ->
            val grouped = entries.groupBy { it.playlistId }
            lists.map { p ->
                val inList = grouped[p.id].orEmpty().sortedBy { it.position }.mapNotNull { byId[it.songId] }
                PlaylistSummary(p.id, p.name, inList.size, inList.firstOrNull())
            } as List<PlaylistSummary>?
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, eagerlyForUi, null)

    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        combine(playlistRepo.observeEntries(playlistId), songsById) { entries, byId ->
            entries.mapNotNull { byId[it.songId] }
        }

    // ---------------------------------------------------------- albums, artists

    val albums: StateFlow<List<AlbumInfo>> =
        songs.map { buildAlbums(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, eagerlyForUi, emptyList())

    val artists: StateFlow<List<ArtistInfo>> =
        songs.map { buildArtists(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, eagerlyForUi, emptyList())

    // ----------------------------------------------------------- smart lists

    private val statsList = stats.observeAll()

    val recentlyAdded: StateFlow<List<Song>> =
        songs.map { list -> list.sortedByDescending { it.dateAddedSec }.take(SECTION_LIMIT) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, eagerlyForUi, emptyList())

    val recentlyPlayed: StateFlow<List<Song>> =
        combine(statsList, songsById) { rows, byId ->
            rows.sortedByDescending { it.lastPlayedAt }.mapNotNull { byId[it.songId] }.take(SECTION_LIMIT)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, eagerlyForUi, emptyList())

    val mostPlayed: StateFlow<List<Song>> =
        combine(statsList, songsById) { rows, byId ->
            rows.sortedWith(compareByDescending<PlayStatsEntity> { it.playCount }.thenByDescending { it.lastPlayedAt })
                .mapNotNull { byId[it.songId] }
                .take(SECTION_LIMIT)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, eagerlyForUi, emptyList())

    /** songId -> how many times it was played. Real numbers from the play history. */
    val playCounts: StateFlow<Map<Long, Int>> =
        statsList.map { rows -> rows.associate { it.songId to it.playCount } }
            .stateIn(viewModelScope, eagerlyForUi, emptyMap())

    // ---------------------------------------------------------------- messages

    private val ownMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short messages for a snackbar ("Added to queue", "Couldn't play that song"...). */
    val messages: Flow<String> = merge(ownMessages, playback.messages)

    // ----------------------------------------------------------------- actions

    fun rescan() = library.refresh()

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val nowFavorite = favorites.toggle(song.id)
            ownMessages.tryEmit(if (nowFavorite) "Added to Liked Music" else "Removed from Liked Music")
        }
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepo.create(name, songs.map { it.id })
            ownMessages.tryEmit(
                if (songs.isEmpty()) "Created playlist \"${name.trim()}\""
                else "Created \"${name.trim()}\" with ${countText(songs.size)}"
            )
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepo.rename(playlistId, name) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepo.delete(playlistId)
            ownMessages.tryEmit("Playlist deleted")
        }
    }

    fun addToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            val added = playlistRepo.addSongs(playlistId, songIds)
            val name = playlists.value?.firstOrNull { it.id == playlistId }?.name.orEmpty()
            ownMessages.tryEmit(
                when {
                    added == 0 -> "Already in \"$name\""
                    else -> "Added ${countText(added)} to \"$name\""
                }
            )
        }
    }

    fun removeFromPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch { playlistRepo.removeSong(playlistId, song.id) }
    }

    fun playSongs(list: List<Song>, index: Int) = playback.playSongs(list, index)

    fun shufflePlay(list: List<Song>) {
        if (list.isEmpty()) return
        playback.playSongs(list, list.indices.random(), forceShuffle = true)
    }

    fun playNext(song: Song) {
        if (playback.playNext(song)) ownMessages.tryEmit("Will play next")
    }

    fun addToQueue(song: Song) {
        if (playback.addToQueue(song)) ownMessages.tryEmit("Added to queue")
    }

    // ------------------------------------------------------------------ helpers

    private fun countText(n: Int) = if (n == 1) "1 song" else "$n songs"

    private fun buildAlbums(songs: List<Song>): List<AlbumInfo> =
        songs.groupBy { if (it.albumId > 0) it.albumId.toString() else "name:${it.album}" }
            .map { (_, inAlbum) ->
                val first = inAlbum.first()
                val id = if (first.albumId > 0) first.albumId else -(first.album.hashCode().toLong().absoluteValue) - 1
                val artist = inAlbum.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
                AlbumInfo(
                    id = id,
                    name = first.album,
                    artist = artist,
                    songs = inAlbum.sortedWith(
                        compareBy<Song> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                    )
                )
            }
            .sortedWith(compareBy<AlbumInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun buildArtists(songs: List<Song>): List<ArtistInfo> =
        songs.groupBy { it.artist.trim().lowercase() }
            .map { (_, byArtist) ->
                ArtistInfo(
                    name = byArtist.first().artist,
                    albumCount = byArtist.map { if (it.albumId > 0) it.albumId.toString() else "name:${it.album}" }
                        .distinct().size,
                    songs = byArtist
                )
            }
            .sortedWith(compareBy<ArtistInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.name })
}
