package com.roundmusic.player.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.ui.LocalPlayGate
import com.roundmusic.player.ui.components.ArtworkImage
import com.roundmusic.player.ui.components.PlaceholderArt
import com.roundmusic.player.ui.components.SongRow
import com.roundmusic.player.ui.components.formatDuration
import com.roundmusic.player.ui.components.songCountText

/**
 * One page for any list of songs: playlists, Liked Music, smart playlists and albums.
 * Header with picture, name, count, and real Play / Shuffle buttons.
 */
@Composable
fun SongListScreen(
    title: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emptyText: String = "No songs here yet.",
    topBarActions: @Composable RowScope.() -> Unit = {},
    headerExtra: @Composable () -> Unit = {},
    extraInfo: (Song) -> String? = { null },
    menuExtras: @Composable (Song, () -> Unit) -> Unit = { _, _ -> }
) {
    val playGate = LocalPlayGate.current
    val totalMs = songs.sumOf { it.durationMs }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            topBarActions()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val cover = songs.firstOrNull()
                        if (cover != null) {
                            ArtworkImage(
                                song = cover,
                                modifier = Modifier.size(112.dp),
                                shape = RoundedCornerShape(20.dp),
                                large = true
                            )
                        } else {
                            PlaceholderArt(modifier = Modifier.size(112.dp), shape = RoundedCornerShape(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = songCountText(songs.size) +
                                    if (songs.isNotEmpty()) " · ${formatTotal(totalMs)}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { playGate { onPlayAt(0) } },
                            enabled = songs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("  Play")
                        }
                        OutlinedButton(
                            onClick = { playGate { onShuffle() } },
                            enabled = songs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Text("  Shuffle")
                        }
                    }
                    headerExtra()
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (songs.isEmpty()) {
                item {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        extraInfo = extraInfo(song),
                        onClick = { playGate { onPlayAt(index) } },
                        menuExtras = { dismiss -> menuExtras(song, dismiss) }
                    )
                }
            }
        }
    }
}

/** "2 hr 5 min" or "14 min" or "3:05". */
fun formatTotal(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours hr $minutes min"
        totalMinutes > 0 -> "$totalMinutes min"
        else -> formatDuration(ms)
    }
}
