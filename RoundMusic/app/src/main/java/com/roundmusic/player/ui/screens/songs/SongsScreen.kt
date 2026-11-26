package com.roundmusic.player.ui.screens.songs

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roundmusic.player.data.model.SortField
import com.roundmusic.player.data.model.SortOrder
import com.roundmusic.player.ui.LocalPlayGate
import com.roundmusic.player.ui.components.AppTitle
import com.roundmusic.player.ui.components.MessageState
import com.roundmusic.player.ui.components.SearchField
import com.roundmusic.player.ui.components.SongRow
import com.roundmusic.player.ui.components.songCountText
import com.roundmusic.player.viewmodel.SongsViewModel
import java.text.DateFormat
import java.util.Date

/** The "Search" tab: all songs, with search and sort. */
@Composable
fun SongsScreen(viewModel: SongsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playGate = LocalPlayGate.current

    // The text box keeps its own text (so typing never lags) and tells the ViewModel about it.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.onQueryChange(query) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Column(modifier = Modifier.fillMaxSize()) {

        AppTitle()

        SearchField(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            onSearch = { focusManager.clearFocus() }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val countText = when {
                state.isLoading || state.error != null -> ""
                query.isBlank() -> songCountText(state.totalCount)
                else -> "${state.songs.size} of ${songCountText(state.totalCount)}"
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::rescan) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
            }
            SortMenu(sort = state.sort, onFieldSelected = viewModel::onSortFieldSelected)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.error != null -> MessageState(
                    title = "Couldn't read your music",
                    body = state.error.orEmpty(),
                    actionLabel = "Try again",
                    onAction = viewModel::rescan
                )

                state.totalCount == 0 -> MessageState(
                    title = "No music found",
                    body = "RoundMusic shows the audio files that Android has found on your phone " +
                        "and SD card. Add some songs, then tap Rescan. Folders that contain a " +
                        ".nomedia file are hidden by Android.",
                    actionLabel = "Rescan",
                    onAction = viewModel::rescan
                )

                state.songs.isEmpty() -> MessageState(
                    title = "No matches",
                    body = "No song, artist or album matches \"${query.trim()}\"."
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(items = state.songs, key = { it.id }) { song ->
                        val extra = when (state.sort.field) {
                            SortField.DATE_ADDED -> dateFormat.format(Date(song.dateAddedSec * 1000))
                            SortField.SIZE -> Formatter.formatShortFileSize(context, song.sizeBytes)
                            else -> null
                        }
                        SongRow(
                            song = song,
                            extraInfo = extra,
                            onClick = { playGate { viewModel.onSongClick(song) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    sort: SortOrder,
    onFieldSelected: (SortField) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null)
            Text(text = "  ${sort.field.label}")
            Icon(
                imageVector = if (sort.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = if (sort.ascending) "Ascending" else "Descending"
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(field.label) },
                    onClick = {
                        onFieldSelected(field)
                        open = false
                    },
                    trailingIcon = {
                        if (field == sort.field) {
                            Icon(
                                imageVector = if (sort.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}
