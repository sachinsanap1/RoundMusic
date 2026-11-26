package com.roundmusic.player.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.ui.components.ArtworkImage
import com.roundmusic.player.ui.components.MessageState
import com.roundmusic.player.ui.components.SearchField
import com.roundmusic.player.viewmodel.LibraryViewModel

/** Tick as many songs as you like, then add them all to the playlist in one go. */
@Composable
fun SongPickerScreen(
    playlistId: Long,
    viewModel: LibraryViewModel,
    onClose: () -> Unit
) {
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()
    val inPlaylist by remember(playlistId) { viewModel.playlistSongs(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList<Song>())

    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    val focusManager = LocalFocusManager.current

    val existing = remember(inPlaylist) { inPlaylist.map { it.id }.toSet() }
    val candidates = remember(allSongs, existing, query) {
        val q = query.trim()
        allSongs
            .filter { it.id !in existing }
            .filter {
                q.isEmpty() ||
                    it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
            .sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
            Text(
                text = "Add songs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    viewModel.addToPlaylist(playlistId, selected.toList())
                    onClose()
                }
            ) {
                Text(if (selected.isEmpty()) "Add" else "Add (${selected.size})")
            }
        }

        SearchField(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            onSearch = { focusManager.clearFocus() }
        )
        Spacer(Modifier.height(8.dp))

        if (candidates.isEmpty()) {
            MessageState(
                title = if (query.isBlank()) "Nothing to add" else "No matches",
                body = if (query.isBlank()) "Every song on your phone is already in this playlist."
                else "No song, artist or album matches \"${query.trim()}\"."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(candidates, key = { it.id }) { song ->
                    val checked = song.id in selected
                    val toggle = {
                        selected = if (checked) selected - song.id else selected + song.id
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = toggle)
                            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { toggle() })
                        ArtworkImage(song = song, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
