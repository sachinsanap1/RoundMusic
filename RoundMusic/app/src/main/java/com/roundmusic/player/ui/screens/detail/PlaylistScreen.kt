package com.roundmusic.player.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.ui.components.ConfirmDialog
import com.roundmusic.player.ui.components.PlaylistNameDialog
import com.roundmusic.player.viewmodel.LibraryViewModel

/** One of YOUR playlists: play, shuffle, add several songs, rename, delete, remove a song. */
@Composable
fun PlaylistScreen(
    playlistId: Long,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onAddSongs: () -> Unit
) {
    val summaries by viewModel.playlists.collectAsStateWithLifecycle()
    val playlist = summaries?.firstOrNull { it.id == playlistId }
    val songs by remember(playlistId) { viewModel.playlistSongs(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList<Song>())

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    // The playlist was deleted (here or elsewhere): leave the page.
    LaunchedEffect(summaries, playlist) {
        if (summaries != null && playlist == null) onBack()
    }

    if (playlist == null) {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    SongListScreen(
        title = playlist.name,
        songs = songs,
        subtitle = "Playlist",
        emptyText = "This playlist is empty. Tap \"Add songs\" to fill it.",
        onBack = onBack,
        onPlayAt = { viewModel.playSongs(songs, it) },
        onShuffle = { viewModel.shufflePlay(songs) },
        topBarActions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = {
                            menuOpen = false
                            showRename = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            menuOpen = false
                            showDelete = true
                        }
                    )
                }
            }
        },
        headerExtra = {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAddSongs, modifier = Modifier.padding(0.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("  Add songs")
            }
        },
        menuExtras = { song, dismiss ->
            DropdownMenuItem(
                text = { Text("Remove from this playlist") },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                onClick = {
                    dismiss()
                    viewModel.removeFromPlaylist(playlistId, song)
                }
            )
        }
    )

    if (showRename) {
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = playlist.name,
            confirmLabel = "Rename",
            onConfirm = {
                viewModel.renamePlaylist(playlistId, it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${playlist.name}\" will be deleted. Your songs are not deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                showDelete = false
                viewModel.deletePlaylist(playlistId)
            },
            onDismiss = { showDelete = false }
        )
    }
}
