package com.roundmusic.player.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roundmusic.player.AppContainer
import com.roundmusic.player.ui.components.AppBackground
import com.roundmusic.player.ui.permission.AudioPermission
import com.roundmusic.player.ui.screens.main.MainScaffold
import com.roundmusic.player.ui.screens.nowplaying.NowPlayingScreen
import com.roundmusic.player.ui.screens.permission.PermissionScreen
import com.roundmusic.player.ui.screens.welcome.WelcomeScreen
import com.roundmusic.player.viewmodel.AppViewModel
import com.roundmusic.player.viewmodel.LibraryViewModel
import com.roundmusic.player.viewmodel.NowPlayingViewModel
import com.roundmusic.player.viewmodel.SongsViewModel

/**
 * Decides which screen to show:
 *  1. still reading settings          -> empty dark screen (a split second)
 *  2. first launch                    -> Welcome screen (explains permission, then asks)
 *  3. permission missing              -> Permission screen
 *  4. permission granted              -> Songs screen (tap a song to play)
 */
@Composable
fun RoundMusicRoot(container: AppContainer) {
    val context = LocalContext.current

    val appViewModel: AppViewModel = viewModel(
        factory = viewModelFactory { initializer { AppViewModel(container.settingsRepository) } }
    )
    val onboardingDone by appViewModel.onboardingDone.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(AudioPermission.isGranted(context)) }

    // Check again every time the app comes back to the screen (for example from the phone's Settings).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = AudioPermission.isGranted(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        appViewModel.completeOnboarding()
    }

    // Android 13+: the playback notification needs this permission. We ask the first time you play
    // a song (when it is obvious why), then start playing no matter what you answer.
    var pendingPlay by remember { mutableStateOf<(() -> Unit)?>(null) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingPlay?.invoke()
        pendingPlay = null
    }
    val playGate: (() -> Unit) -> Unit = { play ->
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !askedForNotifications &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            askedForNotifications = true
            pendingPlay = play
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            play()
        }
    }

    when {
        onboardingDone == null -> AppBackground { }

        onboardingDone == false -> WelcomeScreen(
            onGetStarted = {
                if (granted) {
                    appViewModel.completeOnboarding()
                } else {
                    permissionLauncher.launch(AudioPermission.permission)
                }
            }
        )

        !granted -> PermissionScreen(
            onAllow = { permissionLauncher.launch(AudioPermission.permission) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // Very rare: the phone has no settings screen for this. Nothing else we can do.
                }
            }
        )

        else -> {
            val songsViewModel: SongsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SongsViewModel(
                            container.libraryRepository,
                            container.settingsRepository,
                            container.playbackController
                        )
                    }
                }
            )
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LibraryViewModel(
                            container.libraryRepository,
                            container.favoritesRepository,
                            container.playlistRepository,
                            container.statsRepository,
                            container.playbackController
                        )
                    }
                }
            )
            val nowPlayingViewModel: NowPlayingViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        NowPlayingViewModel(container.playbackController, container.favoritesRepository)
                    }
                }
            )
            var showNowPlaying by rememberSaveable { mutableStateOf(false) }

            CompositionLocalProvider(LocalPlayGate provides playGate) {
                Box {
                    MainScaffold(
                        libraryViewModel = libraryViewModel,
                        songsViewModel = songsViewModel,
                        nowPlayingViewModel = nowPlayingViewModel,
                        onOpenNowPlaying = { showNowPlaying = true }
                    )
                    AnimatedVisibility(
                        visible = showNowPlaying,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        NowPlayingScreen(
                            viewModel = nowPlayingViewModel,
                            onClose = { showNowPlaying = false }
                        )
                    }
                }
            }
        }
    }
}
