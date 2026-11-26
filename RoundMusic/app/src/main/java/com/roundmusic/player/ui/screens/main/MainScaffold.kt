package com.roundmusic.player.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.ui.LocalCurrentSongId
import com.roundmusic.player.ui.LocalIsPlaying
import com.roundmusic.player.ui.LocalSongActions
import com.roundmusic.player.ui.SongActions
import com.roundmusic.player.ui.components.AddToPlaylistDialog
import com.roundmusic.player.ui.components.AppBackground
import com.roundmusic.player.ui.components.MiniPlayer
import com.roundmusic.player.ui.components.PlaylistNameDialog
import com.roundmusic.player.ui.screens.detail.ArtistScreen
import com.roundmusic.player.ui.screens.detail.PlaylistScreen
import com.roundmusic.player.ui.screens.detail.SongListScreen
import com.roundmusic.player.ui.screens.detail.SongPickerScreen
import com.roundmusic.player.ui.screens.home.HomeScreen
import com.roundmusic.player.ui.screens.library.LibraryScreen
import com.roundmusic.player.ui.screens.songs.SongsScreen
import com.roundmusic.player.viewmodel.LibraryViewModel
import com.roundmusic.player.viewmodel.NowPlayingViewModel
import com.roundmusic.player.viewmodel.SongsViewModel

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Search("Search", Icons.Rounded.Search),
    Library("Library", Icons.Rounded.LibraryMusic)
}

/**
 * The main app frame: bottom navigation (Home, Search, Library), the mini player above it,
 * pages that open on top of a tab (playlist, album, artist...), and the shared dialogs/messages.
 * The Settings tab is added in Phase 7, when the Settings screen exists.
 */
@Composable
fun MainScaffold(
    libraryViewModel: LibraryViewModel,
    songsViewModel: SongsViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    onOpenNowPlaying: () -> Unit
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val backStack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    fun open(route: Route) {
        backStack.add(route.encode())
    }
    fun back() {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
    }

    BackHandler(enabled = backStack.isNotEmpty()) { back() }

    // ---- messages ("Added to queue"...) shown as a snackbar above the bottom bar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        libraryViewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // ---- "three dots" actions shared by every song row
    val favoriteIds by libraryViewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by libraryViewModel.playlists.collectAsStateWithLifecycle()
    val currentSongId by nowPlayingViewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by nowPlayingViewModel.isPlaying.collectAsStateWithLifecycle()

    var addTargets by remember { mutableStateOf<List<Song>?>(null) }
    var createRequest by remember { mutableStateOf<List<Song>?>(null) }

    val songActions = remember(favoriteIds) {
        SongActions(
            favoriteIds = favoriteIds,
            toggleFavorite = libraryViewModel::toggleFavorite,
            playNext = libraryViewModel::playNext,
            addToQueue = libraryViewModel::addToQueue,
            addToPlaylist = { addTargets = it }
        )
    }

    CompositionLocalProvider(
        LocalSongActions provides songActions,
        LocalCurrentSongId provides currentSongId,
        LocalIsPlaying provides isPlaying
    ) {
        AppBackground {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Column {
                        MiniPlayerHost(nowPlayingViewModel, onOpenNowPlaying)
                        NavigationBar {
                            MainTab.entries.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = tabIndex == index,
                                    onClick = {
                                        tabIndex = index
                                        backStack.clear()
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    val route = backStack.lastOrNull()?.let { Route.parse(it) }
                    if (route != null) {
                        RouteContent(
                            route = route,
                            viewModel = libraryViewModel,
                            onBack = ::back,
                            onOpen = ::open
                        )
                    } else {
                        when (MainTab.entries[tabIndex]) {
                            MainTab.Home -> HomeScreen(
                                viewModel = libraryViewModel,
                                onOpenSmart = { open(Route.Smart(it)) }
                            )

                            MainTab.Search -> SongsScreen(songsViewModel)

                            MainTab.Library -> LibraryScreen(
                                viewModel = libraryViewModel,
                                onOpen = ::open,
                                onCreatePlaylist = { createRequest = emptyList() }
                            )
                        }
                    }
                }
            }

            // ---- dialogs
            addTargets?.let { targets ->
                AddToPlaylistDialog(
                    playlists = playlists.orEmpty(),
                    onPick = { playlist ->
                        libraryViewModel.addToPlaylist(playlist.id, targets.map { it.id })
                        addTargets = null
                    },
                    onCreateNew = {
                        createRequest = targets
                        addTargets = null
                    },
                    onDismiss = { addTargets = null }
                )
            }
            createRequest?.let { songs ->
                PlaylistNameDialog(
                    title = "New playlist",
                    initialName = "",
                    confirmLabel = "Create",
                    onConfirm = { name ->
                        libraryViewModel.createPlaylist(name, songs)
                        createRequest = null
                    },
                    onDismiss = { createRequest = null }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerHost(viewModel: NowPlayingViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    MiniPlayer(
        state = state,
        positionMs = position,
        onOpen = onOpen,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous
    )
}

/** The page that is open on top of a tab. */
@Composable
private fun RouteContent(
    route: Route,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpen: (Route) -> Unit
) {
    when (route) {
        is Route.Playlist -> PlaylistScreen(
            playlistId = route.id,
            viewModel = viewModel,
            onBack = onBack,
            onAddSongs = { onOpen(Route.Picker(route.id)) }
        )

        is Route.Picker -> SongPickerScreen(
            playlistId = route.playlistId,
            viewModel = viewModel,
            onClose = onBack
        )

        is Route.Artist -> ArtistScreen(
            artistName = route.name,
            viewModel = viewModel,
            onBack = onBack,
            onOpen = onOpen
        )

        is Route.Album -> {
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            val album = albums.firstOrNull { it.id == route.id }
            // The album disappeared (its songs were deleted): leave the page.
            LaunchedEffect(albums, album) {
                if (album == null && albums.isNotEmpty()) onBack()
            }
            if (album == null) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                SongListScreen(
                    title = album.name,
                    subtitle = album.artist,
                    songs = album.songs,
                    onBack = onBack,
                    onPlayAt = { viewModel.playSongs(album.songs, it) },
                    onShuffle = { viewModel.shufflePlay(album.songs) }
                )
            }
        }

        is Route.Smart -> {
            val flow = when (route.kind) {
                SmartKind.FAVORITES -> viewModel.favoriteSongs
                SmartKind.RECENTLY_ADDED -> viewModel.recentlyAdded
                SmartKind.RECENTLY_PLAYED -> viewModel.recentlyPlayed
                SmartKind.MOST_PLAYED -> viewModel.mostPlayed
            }
            val songs by flow.collectAsStateWithLifecycle()
            val playCounts by viewModel.playCounts.collectAsStateWithLifecycle()
            SongListScreen(
                title = route.kind.title,
                subtitle = "Smart playlist",
                songs = songs,
                emptyText = when (route.kind) {
                    SmartKind.FAVORITES -> "Tap the heart on a song to add it here."
                    SmartKind.RECENTLY_ADDED -> "No songs found on your phone yet."
                    SmartKind.RECENTLY_PLAYED, SmartKind.MOST_PLAYED -> "Play some songs and they will show up here."
                },
                onBack = onBack,
                onPlayAt = { viewModel.playSongs(songs, it) },
                onShuffle = { viewModel.shufflePlay(songs) },
                extraInfo = { song ->
                    if (route.kind == SmartKind.MOST_PLAYED) {
                        val plays = playCounts[song.id] ?: 0
                        if (plays == 1) "1 play" else "$plays plays"
                    } else {
                        null
                    }
                }
            )
        }
    }
}
