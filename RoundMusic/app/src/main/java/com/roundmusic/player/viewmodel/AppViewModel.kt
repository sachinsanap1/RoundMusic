package com.roundmusic.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roundmusic.player.data.datastore.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds app-wide state: has the Welcome screen been shown yet? */
class AppViewModel(private val settings: SettingsRepository) : ViewModel() {

    /** null = still reading from disk, true/false = known. */
    val onboardingDone: StateFlow<Boolean?> =
        settings.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone() }
    }
}
