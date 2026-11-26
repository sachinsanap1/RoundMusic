package com.roundmusic.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.roundmusic.player.ui.theme.PurpleBackground
import com.roundmusic.player.ui.theme.PurpleBackgroundTop

/** The dark violet gradient used behind every screen. */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PurpleBackgroundTop, PurpleBackground, PurpleBackground))),
        content = content
    )
}
