package com.roundmusic.player.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundmusic.player.ui.LocalPlayGate
import com.roundmusic.player.ui.components.ArtworkCard
import com.roundmusic.player.ui.components.ArtworkImage
import com.roundmusic.player.ui.components.PlaceholderArt
import com.roundmusic.player.ui.components.SectionHeader
import com.roundmusic.player.ui.components.SongRow
import com.roundmusic.player.ui.components.songCountText
import com.roundmusic.player.ui.screens.main.Route
import com.roundmusic.player.viewmodel.LibraryViewModel

/**
 * Artist page. Only REAL local numbers: albums, songs and how many times YOU played them.
 * No monthly listeners, followers or streaming numbers.
 */
@Composable
fun ArtistScreen(
    artistName: String,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpen: (Route) -> Unit
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playCounts by viewModel.playCounts.collectAsStateWithLifecycle()
    val playGate = LocalPlayGate.current

    val artist = artists.firstOrNull { it.name.trim().equals(artistName.trim(), ignoreCase = true) }
    val artistAlbums = remember(albums, artistName) {
        albums.filter { album -> album.songs.any { it.artist.trim().equals(artistName.trim(), ignoreCase = true) } }
    }

    // "Popular" = the songs YOU played most, then the rest by name.
    val popular = remember(artist, playCounts) {
        artist?.songs.orEmpty().sortedWith(
            compareByDescending<com.roundmusic.player.data.model.Song> { playCounts[it.id] ?: 0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
    }
    val totalPlays = popular.sumOf { playCounts[it.id] ?: 0 }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }

        if (artist == null) {
            Text(
                text = "This artist is no longer on your phone.",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val cover = artist.songs.firstOrNull()
                    if (cover != null) {
                        ArtworkImage(
                            song = cover,
                            modifier = Modifier.size(170.dp),
                            shape = RoundedCornerShape(36.dp),
                            large = true
                        )
                    } else {
                        PlaceholderArt(modifier = Modifier.size(170.dp), shape = RoundedCornerShape(36.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(artist.albumCount.toString(), if (artist.albumCount == 1) "Album" else "Albums")
                        Stat(artist.songs.size.toString(), if (artist.songs.size == 1) "Song" else "Songs")
                        Stat(totalPlays.toString(), if (totalPlays == 1) "Play" else "Plays")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { playGate { viewModel.playSongs(popular, 0) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("  Play")
                        }
                        OutlinedButton(
                            onClick = { playGate { viewModel.shufflePlay(artist.songs) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Text("  Shuffle")
                        }
                    }
                }
            }

            if (artistAlbums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(artistAlbums, key = { it.id }) { album ->
                            ArtworkCard(
                                song = album.songs.firstOrNull(),
                                title = album.name,
                                subtitle = songCountText(album.songs.size),
                                onClick = { onOpen(Route.Album(album.id)) },
                                size = 130.dp
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Popular") }
            itemsIndexed(popular, key = { _, song -> song.id }) { index, song ->
                val plays = playCounts[song.id] ?: 0
                SongRow(
                    song = song,
                    extraInfo = if (plays > 0) (if (plays == 1) "1 play" else "$plays plays") else null,
                    onClick = { playGate { viewModel.playSongs(popular, index) } }
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
