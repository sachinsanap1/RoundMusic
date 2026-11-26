package com.roundmusic.player.ui.screens.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.ui.components.ArtworkImage
import com.roundmusic.player.ui.components.LocalWaveformLoader
import com.roundmusic.player.ui.components.WaveformBar
import com.roundmusic.player.ui.components.formatDuration
import com.roundmusic.player.ui.theme.PurpleAccentLight
import com.roundmusic.player.ui.theme.PurpleBackground
import com.roundmusic.player.viewmodel.NowPlayingViewModel
import kotlin.math.roundToInt

/**
 * The full player screen. Design: the album art fills the whole screen and fades into the dark theme
 * at the bottom (like the reference). Everything on it works:
 *  - swipe the picture left/right to change song
 *  - heart = Liked Music, "Lyrics" button, shuffle, previous, play/pause, next, repeat
 *  - a REAL waveform seek bar (tap or drag to seek) with the times on both sides
 *  - volume (the phone's media volume)
 *  - the "Music List" pill opens the queue (tap or swipe up)
 */
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val song = state.currentSong

    // Nothing is loaded any more (for example the last song was removed from the queue): go back.
    LaunchedEffect(song == null) { if (song == null) onClose() }

    BackHandler { if (lyricsOpen) lyricsOpen = false else onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurpleBackground)
    ) {
        if (song != null) {
            // ---- the picture fills the screen; swiping it changes the song
            ArtworkPager(
                queue = queue,
                currentIndex = state.queueIndex,
                onPageSettled = { viewModel.skipToQueueItem(it, play = false) }
            )

            // ---- dark fade: a little at the top (so the text is readable), a lot at the bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.45f),
                                0.20f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.68f to PurpleBackground.copy(alpha = 0.88f),
                                1.00f to PurpleBackground
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // ---- header: drag handle + "Play Now: Album «...»". Tap it to close.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClose)
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Play Now: Album «${song.album}»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Text(
                        text = "${state.queueIndex + 1} of ${state.queueSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                Spacer(Modifier.weight(1f))

                // ---- heart (Liked Music) and Lyrics
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFavorite = song.id in favoriteIds
                    IconButton(
                        onClick = { viewModel.toggleFavorite(song.id) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from Liked Music" else "Add to Liked Music",
                            tint = if (isFavorite) PurpleAccentLight else Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { lyricsOpen = true }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- title and artist, centered
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 4.dp)
                )

                Spacer(Modifier.height(16.dp))

                // ---- shuffle / previous / big white play / next / repeat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = if (state.shuffle) "Shuffle is on" else "Shuffle is off",
                            tint = if (state.shuffle) PurpleAccentLight else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = viewModel::playPause,
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    IconButton(
                        onClick = viewModel::next,
                        enabled = state.hasNext,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White.copy(alpha = if (state.hasNext) 1f else 0.35f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = viewModel::cycleRepeat) {
                        val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                        Icon(
                            imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                            else Icons.Rounded.Repeat,
                            contentDescription = when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> "Repeat one song"
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                else -> "Repeat is off"
                            },
                            tint = if (repeatOn) PurpleAccentLight else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ---- waveform seek bar with times on both sides
                WaveformSeekRow(
                    viewModel = viewModel,
                    song = song,
                    durationMs = state.durationMs,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // ---- volume
                if (state.volumeAvailable && state.maxVolume > state.minVolume) {
                    val range = (state.maxVolume - state.minVolume).toFloat()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = ((state.volume - state.minVolume) / range).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                viewModel.setVolume(state.minVolume + (fraction * range).roundToInt())
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---- "Music List" pill: opens the queue (tap or swipe up)
                MusicListPill(
                    onOpen = { queueOpen = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 14.dp)
                )
            }

            AnimatedVisibility(
                visible = lyricsOpen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                LyricsPanel(song = song, onClose = { lyricsOpen = false })
            }
        }
    }

    if (queueOpen) {
        QueueSheet(
            queue = queue,
            currentIndex = state.queueIndex,
            onDismiss = { queueOpen = false },
            onPlayItem = { viewModel.skipToQueueItem(it, play = true) },
            onMove = viewModel::moveQueueItem,
            onRemove = viewModel::removeQueueItem
        )
    }
}

/**
 * The full-screen picture. The page you are on always follows the song that is playing, and swiping
 * to another page jumps the player to that song. (Pages are in queue order; with Shuffle on, the Next
 * button picks a random song, so the pager may jump further than one page.)
 */
@Composable
private fun ArtworkPager(
    queue: List<Song>,
    currentIndex: Int,
    onPageSettled: (Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    ) { queue.size }

    val latestIndex by rememberUpdatedState(currentIndex)
    val latestOnSettled by rememberUpdatedState(onPageSettled)

    // Player changed song (next button, auto-advance, queue tap): move the pager.
    LaunchedEffect(currentIndex, queue.size) {
        if (currentIndex in queue.indices && pagerState.currentPage != currentIndex) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    // You swiped to another page: tell the player.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != latestIndex) latestOnSettled(page)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val pageSong = queue.getOrNull(page)
        if (pageSong != null) {
            ArtworkImage(
                song = pageSong,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                large = true
            )
        }
    }
}

/**
 * Time on the left, the real waveform in the middle, total time on the right.
 * This part reads the play position itself, so only it redraws twice a second.
 */
@Composable
private fun WaveformSeekRow(
    viewModel: NowPlayingViewModel,
    song: Song,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val position by viewModel.positionMs.collectAsStateWithLifecycle()
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val loader = LocalWaveformLoader.current
    val peaks by produceState<FloatArray?>(initialValue = loader.cached(song), key1 = song.id) {
        value = loader.cached(song)
        if (value == null) value = loader.load(song)
    }

    val playedFraction = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = dragFraction ?: playedFraction
    val shownPositionMs = (shownFraction * durationMs).toLong()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDuration(shownPositionMs),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        WaveformBar(
            peaks = peaks,
            progress = shownFraction,
            onScrub = { dragFraction = it },
            onScrubEnd = { fraction ->
                if (durationMs > 0) viewModel.seekTo((fraction * durationMs).toLong())
                dragFraction = null
            },
            onScrubCancel = { dragFraction = null },
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 10.dp)
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/** The white "Music List ^" pill. Tap it or swipe up on it to open the queue. */
@Composable
private fun MusicListPill(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        modifier = modifier.pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onVerticalDrag = { _, dragAmount -> total += dragAmount },
                onDragEnd = { if (total < -40f) onOpen() }
            )
        },
        shape = RoundedCornerShape(50),
        color = Color.White,
        contentColor = Color.Black
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Music List", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Open the queue", modifier = Modifier.size(20.dp))
        }
    }
}
