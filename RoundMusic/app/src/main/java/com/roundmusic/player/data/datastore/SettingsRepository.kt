package com.roundmusic.player.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roundmusic.player.data.model.SortField
import com.roundmusic.player.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// There must be exactly ONE of these per app, so it lives at the top of the file.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Where the player was when the app last stopped, so it can come back to the same spot. */
data class ResumeState(
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int
)

/** Saves small settings on the phone so they survive an app restart. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val RESUME_INDEX = intPreferencesKey("resume_index")
        val RESUME_POSITION = longPreferencesKey("resume_position_ms")
        val RESUME_SHUFFLE = booleanPreferencesKey("resume_shuffle")
        val RESUME_REPEAT = intPreferencesKey("resume_repeat")
    }

    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        // If the file is unreadable, fall back to defaults instead of crashing.
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }

    val sortOrder: Flow<SortOrder> = data.map { prefs ->
        val field = prefs[Keys.SORT_FIELD]
            ?.let { name -> SortField.entries.firstOrNull { it.name == name } }
            ?: SortField.TITLE
        SortOrder(field, prefs[Keys.SORT_ASCENDING] ?: field.defaultAscending)
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit {
            it[Keys.SORT_FIELD] = order.field.name
            it[Keys.SORT_ASCENDING] = order.ascending
        }
    }

    suspend fun saveResumeState(state: ResumeState) {
        dataStore.edit {
            it[Keys.RESUME_INDEX] = state.index
            it[Keys.RESUME_POSITION] = state.positionMs
            it[Keys.RESUME_SHUFFLE] = state.shuffle
            it[Keys.RESUME_REPEAT] = state.repeatMode
        }
    }

    suspend fun loadResumeState(): ResumeState {
        val prefs = data.first()
        return ResumeState(
            index = prefs[Keys.RESUME_INDEX] ?: 0,
            positionMs = prefs[Keys.RESUME_POSITION] ?: 0L,
            shuffle = prefs[Keys.RESUME_SHUFFLE] ?: false,
            repeatMode = prefs[Keys.RESUME_REPEAT] ?: 0
        )
    }
}
