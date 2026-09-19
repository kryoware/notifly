package ph.notifly.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException
import ph.notifly.ui.theme.NotiflyPalette

class AppPreferences(private val store: DataStore<Preferences>) {
    private val paletteKey = stringPreferencesKey("palette")
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val offlineKey = booleanPreferencesKey("offline")
    private val retentionKey = booleanPreferencesKey("keep_raw_text")
    private val data = store.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }
    val palette = data.map { prefs ->
        NotiflyPalette.entries.firstOrNull { it.name == prefs[paletteKey] } ?: NotiflyPalette.Evergreen
    }
    val onboardingComplete = data.map { it[onboardingKey] ?: false }
    val offline = data.map { it[offlineKey] ?: true }
    val keepRawText = data.map { it[retentionKey] ?: false }
    suspend fun setPalette(value: NotiflyPalette) { store.edit { it[paletteKey] = value.name } }
    suspend fun completeOnboarding() { store.edit { it[onboardingKey] = true } }
    suspend fun setOffline(value: Boolean) { store.edit { it[offlineKey] = value } }
    suspend fun setKeepRawText(value: Boolean) { store.edit { it[retentionKey] = value } }
}
