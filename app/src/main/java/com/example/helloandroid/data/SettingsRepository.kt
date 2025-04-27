package com.example.helloandroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Define keys for preferences
object PreferencesKeys {
    val CATEGORIZATION_TIMER_SECONDS = intPreferencesKey("categorization_timer_seconds")
    val CATEGORIZATION_REQUIRED_PASSES = intPreferencesKey("categorization_required_passes")
    val KNOWN_CARDS_SET = stringSetPreferencesKey("known_cards_set")
}

// Create DataStore instance via extension property on Context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quiz_settings")

class SettingsRepository(private val context: Context) {

    // Default values
    companion object {
        const val DEFAULT_TIMER_SECONDS = 3
        const val DEFAULT_REQUIRED_PASSES = 3
        val DEFAULT_KNOWN_CARDS: Set<String> = emptySet()
    }

    // --- Read Flows ---

    val timerDurationSeconds: Flow<Int> = context.dataStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                emit(emptyPreferences()) // Emit empty preferences on error
            } else {
                throw exception // Rethrow other exceptions
            }
        }.map { preferences ->
            preferences[PreferencesKeys.CATEGORIZATION_TIMER_SECONDS] ?: DEFAULT_TIMER_SECONDS
        }

    val requiredPasses: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[PreferencesKeys.CATEGORIZATION_REQUIRED_PASSES] ?: DEFAULT_REQUIRED_PASSES
        }

    val knownCards: Flow<Set<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[PreferencesKeys.KNOWN_CARDS_SET] ?: DEFAULT_KNOWN_CARDS
        }

    // --- Write Functions ---

    suspend fun saveSettings(timerSeconds: Int, requiredPasses: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CATEGORIZATION_TIMER_SECONDS] = timerSeconds
            preferences[PreferencesKeys.CATEGORIZATION_REQUIRED_PASSES] = requiredPasses
        }
    }

    suspend fun saveCategorizationTimer(timerSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CATEGORIZATION_TIMER_SECONDS] = timerSeconds
        }
    }

    suspend fun saveCategorizationPasses(requiredPasses: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CATEGORIZATION_REQUIRED_PASSES] = requiredPasses
        }
    }


    suspend fun saveKnownCards(knownCardsSet: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KNOWN_CARDS_SET] = knownCardsSet
        }
    }
}