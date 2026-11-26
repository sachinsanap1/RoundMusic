package com.roundmusic.player.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Colors of the intro screen (dark green with a lime accent, like the reference).
private val IntroBackgroundTop = Color(0xFF1B2A12)
private val IntroBackgroundBottom = Color(0xFF0A1206)
private val IntroLime = Color(0xFFB9F02F)
private val IntroLimeDark = Color(0xFF6FA010)
private val IntroTextMuted = Color(0xFFAAB8A0)

/**
 * Shown only on the first launch.
 * It explains WHY we need permission before Android shows its own permission box.
 * The whole bottom pill is the "start" button.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(IntroBackgroundTop, IntroBackgroundBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ---- app name, top center
            Text(
                text = "RoundMusic",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = IntroLime,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // ---- hero picture: big headphones with a soft glow and two sound-wave marks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(IntroLime.copy(alpha = 0.28f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = IntroLime,
                    modifier = Modifier.size(230.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = IntroLime.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 48.dp)
                        .size(24.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = IntroLime.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 48.dp)
                        .size(24.dp)
                )
            }

            // ---- headline and the permission explanation
            Text(
                text = "Start Your\nSonic Journey",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Your songs, playlists and favorites, all on your phone. To show your music, " +
                    "RoundMusic needs permission to read your audio files. Android will ask you next. " +
                    "RoundMusic has no internet access, so nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = IntroTextMuted
            )

            Spacer(Modifier.height(24.dp))

            // ---- the bottom pill (the real "start" button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(38.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onGetStarted)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(IntroLime, IntroLimeDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = Color(0xFF10200A),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Turn on your music",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
