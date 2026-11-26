package com.roundmusic.player.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.roundmusic.player.data.artwork.ArtworkLoader
import com.roundmusic.player.data.model.Song
import com.roundmusic.player.ui.theme.PurpleAccentDark
import com.roundmusic.player.ui.theme.PurpleCardHigh
import com.roundmusic.player.ui.theme.TextSecondary

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader> {
    error("ArtworkLoader was not provided")
}

/** Shows the song's album art, or a purple placeholder with a music note when there is none. */
@Composable
fun ArtworkImage(
    song: Song,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    large: Boolean = false
) {
    val loader = LocalArtworkLoader.current
    val bitmap by produceState<Bitmap?>(initialValue = loader.cached(song, large), key1 = song.id, key2 = large) {
        if (value == null) value = loader.load(song, large)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(PurpleCardHigh, PurpleAccentDark))),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        val image = remember(current) { current?.asImageBitmap() }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

/** The purple "no picture" tile with a music note. */
@Composable
fun PlaceholderArt(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(PurpleCardHigh, PurpleAccentDark))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.fillMaxSize(0.5f)
        )
    }
}
