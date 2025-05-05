package ca.amandeep.path.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepo(private val context: Context) {
    private object PreferencesKeys {
        val SHOW_MODEL_NAME_DEBUG = booleanPreferencesKey("showModelName_debug")
        val AI_SUMMARIZE_ALERTS_DEBUG = booleanPreferencesKey("aiSummarizeAlerts_debug")
        val SHORTEN_NAMES = booleanPreferencesKey("shortenNames")
        val SHOW_OPPOSITE_DIRECTION = booleanPreferencesKey("showOppositeDirection")
        val SHOW_ELEVATOR_ALERTS = booleanPreferencesKey("showElevatorAlerts")
        val SHOW_HELP_GUIDE = booleanPreferencesKey("showHelpGuide")
        val SHOW_DIRECTION_WARNING = booleanPreferencesKey("showDirectionWarning")
    }

    val showModelName: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.SHOW_MODEL_NAME_DEBUG] ?: false
        }

    suspend fun updateShowModelName(show: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.SHOW_MODEL_NAME_DEBUG] = show
        }
    }

    val aiSummarizeAlerts: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.AI_SUMMARIZE_ALERTS_DEBUG] ?: false
        }

    suspend fun updateAiSummarizeAlerts(summarize: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.AI_SUMMARIZE_ALERTS_DEBUG] = summarize
        }
    }

    val shortenNames: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.SHORTEN_NAMES] ?: false
        }

    suspend fun updateShortenNames(shorten: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.SHORTEN_NAMES] = shorten
        }
    }

    val showOppositeDirection: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.SHOW_OPPOSITE_DIRECTION] ?: true
        }

    suspend fun updateShowOppositeDirection(show: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.SHOW_OPPOSITE_DIRECTION] = show
        }
    }

    val showElevatorAlerts: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.SHOW_ELEVATOR_ALERTS] ?: true
        }

    suspend fun updateShowElevatorAlerts(show: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.SHOW_ELEVATOR_ALERTS] = show
        }
    }

    val showHelpGuide: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.SHOW_HELP_GUIDE] ?: true
        }

    suspend fun updateShowHelpGuide(show: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.SHOW_HELP_GUIDE] = show
        }
    }

    val showDirectionWarning: Flow<Boolean> = context.dataStore.data
        .map {
            it[PreferencesKeys.SHOW_DIRECTION_WARNING] ?: true
        }

    suspend fun updateShowDirectionWarning(show: Boolean) {
        context.dataStore.edit {
            it[PreferencesKeys.SHOW_DIRECTION_WARNING] = show
        }
    }
}
