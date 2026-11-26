package com.roundmusic.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roundmusic.player.data.model.Song

/** A square artwork card with title and subtitle (used in the horizontal rows and in grids). */
@Composable
fun ArtworkCard(
    song: Song?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A fixed width (Home rows), or null to fill the available width (grids). */
    size: Dp? = 140.dp
) {
    val cardModifier = if (size != null) Modifier.width(size) else Modifier.fillMaxWidth()
    val artModifier = if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().aspectRatio(1f)
    Column(
        modifier = modifier
            .then(cardModifier)
            .clickable(onClick = onClick)
    ) {
        if (song != null) {
            ArtworkImage(song = song, modifier = artModifier, shape = RoundedCornerShape(20.dp))
        } else {
            PlaceholderArt(modifier = artModifier, shape = RoundedCornerShape(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A list row with an artwork picture (square or round), a title and a subtitle. */
@Composable
fun CollectionRow(
    song: Song?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = if (round) CircleShape else RoundedCornerShape(14.dp)
        if (song != null) {
            ArtworkImage(song = song, modifier = Modifier.size(56.dp), shape = shape)
        } else {
            PlaceholderArt(modifier = Modifier.size(56.dp), shape = shape)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
