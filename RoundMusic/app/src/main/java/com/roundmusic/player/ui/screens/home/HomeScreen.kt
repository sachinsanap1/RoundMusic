package com.roundmusic.player.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.repository.LibraryState
import com.roundmusic.player.ui.LocalPlayGate
import com.roundmusic.player.ui.components.AppTitle
import com.roundmusic.player.ui.components.ArtworkCard
import com.roundmusic.player.ui.components.MessageState
import com.roundmusic.player.ui.components.SectionHeader
import com.roundmusic.player.ui.screens.main.SmartKind
import com.roundmusic.player.viewmodel.LibraryViewModel

/**
 * Home: Recently played, Most played and Recently added as rows of artwork cards.
 * Everything comes from the phone and from your own play history: nothing is invented.
 */
@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    onOpenSmart: (SmartKind) -> Unit
) {
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTitle()

        val current = libraryState
        when {
            current == null -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            current is LibraryState.Error -> MessageState(
                title = "Couldn't read your music",
                body = current.message,
                actionLabel = "Try again",
                onAction = viewModel::rescan
            )

            songs.isEmpty() -> MessageState(
                title = "No music found",
                body = "Add some songs to your phone or SD card, then tap Rescan.",
                actionLabel = "Rescan",
                onAction = viewModel::rescan
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (recentlyPlayed.isNotEmpty()) {
                    item {
                        SongRowSection("Recently played", recentlyPlayed, viewModel::playSongs) { onOpenSmart(SmartKind.RECENTLY_PLAYED) }
                    }
                }
                if (mostPlayed.isNotEmpty()) {
                    item {
                        SongRowSection("Most played", mostPlayed, viewModel::playSongs) { onOpenSmart(SmartKind.MOST_PLAYED) }
                    }
                }
                if (recentlyPlayed.isEmpty() && mostPlayed.isEmpty()) {
                    item {
                        Text(
                            text = "Play some songs and your history will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                item {
                    SongRowSection("Recently added", recentlyAdded, viewModel::playSongs) { onOpenSmart(SmartKind.RECENTLY_ADDED) }
                }
            }
        }
    }
}

@Composable
private fun SongRowSection(
    title: String,
    songs: List<Song>,
    onPlay: (List<Song>, Int) -> Unit,
    onSeeAll: () -> Unit
) {
    val playGate = LocalPlayGate.current
    Column {
        SectionHeader(title = title, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(songs) { index, song ->
                ArtworkCard(
                    song = song,
                    title = song.title,
                    subtitle = song.artist,
                    onClick = { playGate { onPlay(songs, index) } }
                )
            }
        }
    }
}
