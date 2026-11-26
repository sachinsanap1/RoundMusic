package com.roundmusic.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.roundmusic.player.ui.RoundMusicRoot
import com.roundmusic.player.ui.components.LocalArtworkLoader
import com.roundmusic.player.ui.components.LocalWaveformLoader
import com.roundmusic.player.ui.theme.RoundMusicTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The app is always dark, so system bar icons must always be light.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent)
        )

        container = (application as RoundMusicApp).container

        setContent {
            RoundMusicTheme {
                CompositionLocalProvider(
                    LocalArtworkLoader provides container.artworkLoader,
                    LocalWaveformLoader provides container.waveformLoader
                ) {
                    RoundMusicRoot(container)
                }
            }
        }
    }

    // Connect to the music service while the screen is visible, disconnect when it is not.
    // The music itself keeps playing: the service owns the player, not this screen.
    override fun onStart() {
        super.onStart()
        container.playbackController.connect()
    }

    override fun onStop() {
        container.playbackController.disconnect()
        super.onStop()
    }
}
