package com.roundmusic.player.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.roundmusic.player.data.model.ArtistInfo
import com.roundmusic.player.ui.components.ArtworkCard
import com.roundmusic.player.ui.components.ArtworkImage
import com.roundmusic.player.ui.components.CollectionRow
import com.roundmusic.player.ui.components.MessageState
import com.roundmusic.player.ui.components.PlaceholderArt
import com.roundmusic.player.ui.components.SectionHeader
import com.roundmusic.player.ui.components.songCountText
import com.roundmusic.player.ui.screens.main.Route
import com.roundmusic.player.ui.screens.main.SmartKind
import com.roundmusic.player.ui.theme.PurpleAccent
import com.roundmusic.player.ui.theme.PurpleAccentDark
import com.roundmusic.player.viewmodel.LibraryViewModel

private val TAB_NAMES = listOf("Playlists", "Albums", "Artists")

/** Library: Liked Music, new playlist, smart playlists, your playlists, and Albums / Artists tabs. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpen: (Route) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TAB_NAMES.forEachIndexed { index, name ->
                LibraryTab(name = name, selected = tab == index, onClick = { tab = index })
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> PlaylistsTab(viewModel, onOpen, onCreatePlaylist)
                1 -> AlbumsTab(viewModel, onOpen)
                else -> ArtistsTab(viewModel, onOpen)
            }
        }
    }
}

/** A pill like the reference: white with dark text when selected, dark with white text when not. */
@Composable
private fun LibraryTab(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PlaylistsTab(
    viewModel: LibraryViewModel,
    onOpen: (Route) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        item {
            TileRow(
                icon = Icons.Rounded.Favorite,
                title = "Liked Music",
                subtitle = songCountText(favorites.size),
                onClick = { onOpen(Route.Smart(SmartKind.FAVORITES)) }
            )
        }
        item {
            TileRow(
                icon = Icons.Rounded.Add,
                title = "Add new playlist",
                subtitle = "Create an empty playlist",
                onClick = onCreatePlaylist
            )
        }

        item { SectionHeader("Smart playlists") }
        item {
            TileRow(
                icon = Icons.Rounded.NewReleases,
                title = SmartKind.RECENTLY_ADDED.title,
                subtitle = songCountText(recentlyAdded.size),
                onClick = { onOpen(Route.Smart(SmartKind.RECENTLY_ADDED)) }
            )
        }
        item {
            TileRow(
                icon = Icons.Rounded.History,
                title = SmartKind.RECENTLY_PLAYED.title,
                subtitle = songCountText(recentlyPlayed.size),
                onClick = { onOpen(Route.Smart(SmartKind.RECENTLY_PLAYED)) }
            )
        }
        item {
            TileRow(
                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                title = SmartKind.MOST_PLAYED.title,
                subtitle = songCountText(mostPlayed.size),
                onClick = { onOpen(Route.Smart(SmartKind.MOST_PLAYED)) }
            )
        }

        val mine = playlists.orEmpty()
        item { SectionHeader("Your playlists") }
        if (mine.isEmpty()) {
            item {
                Text(
                    text = "You have no playlists yet. Tap \"Add new playlist\" or use the " +
                        "three-dot menu of any song.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        } else {
            items(mine, key = { it.id }) { playlist ->
                CollectionRow(
                    song = playlist.cover,
                    title = playlist.name,
                    subtitle = songCountText(playlist.songCount),
                    onClick = { onOpen(Route.Playlist(playlist.id)) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(viewModel: LibraryViewModel, onOpen: (Route) -> Unit) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    if (albums.isEmpty()) {
        MessageState(title = "No albums", body = "Albums appear here when songs are found on your phone.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            ArtworkCard(
                song = album.songs.firstOrNull(),
                title = album.name,
                subtitle = "${album.artist} · ${songCountText(album.songs.size)}",
                onClick = { onOpen(Route.Album(album.id)) },
                size = null
            )
        }
    }
}

@Composable
private fun ArtistsTab(viewModel: LibraryViewModel, onOpen: (Route) -> Unit) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    var showList by rememberSaveable { mutableStateOf(false) }

    if (artists.isEmpty()) {
        MessageState(title = "No artists", body = "Artists appear here when songs are found on your phone.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (artists.size == 1) "1 artist" else "${artists.size} artists",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showList = !showList }) {
                Text(if (showList) "Carousel view" else "List view")
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showList) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
                ) {
                    items(artists, key = { it.name.trim().lowercase() }) { artist ->
                        CollectionRow(
                            song = artist.songs.firstOrNull(),
                            title = artist.name,
                            subtitle = (if (artist.albumCount == 1) "1 album" else "${artist.albumCount} albums") +
                                " · " + songCountText(artist.songs.size),
                            onClick = { onOpen(Route.Artist(artist.name)) },
                            round = true
                        )
                    }
                }
            } else {
                ArtistCarousel(artists = artists, onOpen = { onOpen(Route.Artist(it.name)) })
            }
        }
    }
}

/**
 * Cover-flow of artists (like the reference): the middle card is big, the side cards are smaller,
 * tilted and faded. Swipe to browse. Tap the middle card to open the artist; tap a side card to bring
 * it to the middle.
 */
@Composable
private fun ArtistCarousel(artists: List<ArtistInfo>, onOpen: (ArtistInfo) -> Unit) {
    val pagerState = rememberPagerState { artists.size }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = 240.dp.coerceAtMost(maxWidth * 0.7f)
        val sidePadding = (maxWidth - cardWidth) / 2

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSize = PageSize.Fixed(cardWidth),
                pageSpacing = 0.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val artist = artists[page]
                // 0 = this card is in the middle, +/-1 = one card away.
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val distance = abs(offset).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = lerp(1f, 0.82f, distance)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.6f, distance)
                            rotationY = offset.coerceIn(-1f, 1f) * 22f
                            translationX = offset * size.width * 0.16f
                            cameraDistance = 14f * density
                        }
                        .zIndex(1f - distance)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                        .clickable {
                            if (pagerState.currentPage == page) {
                                onOpen(artist)
                            } else {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                        }
                ) {
                    val cover = artist.songs.firstOrNull()
                    if (cover != null) {
                        ArtworkImage(
                            song = cover,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(28.dp),
                            large = true
                        )
                    } else {
                        PlaceholderArt(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            shape = RoundedCornerShape(28.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = songCountText(artist.songs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = "${pagerState.currentPage + 1} of ${artists.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/** A row with a purple icon tile instead of artwork (Liked Music, new playlist, smart lists). */
@Composable
private fun TileRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(PurpleAccent, PurpleAccentDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
