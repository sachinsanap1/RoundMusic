package com.roundmusic.player

import android.app.Application
import android.content.ComponentCallbacks2

class RoundMusicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // When the phone is short on memory and we are in the background, free the picture cache.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            container.artworkLoader.clear()
            container.waveformLoader.clear()
        }
    }
}
