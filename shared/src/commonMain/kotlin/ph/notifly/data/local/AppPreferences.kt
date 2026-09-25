package ph.notifly.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.IOException
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed interface PinResult {
    data object Ok : PinResult
    data object Wrong : PinResult
    data class LockedFor(val seconds: Long) : PinResult
}

private const val MAX_PIN_FAILURES = 5
private val PIN_LOCKOUT = 30.seconds

class AppPreferences(private val store: DataStore<Preferences>) {
    private val paletteKey = stringPreferencesKey("palette")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val offlineKey = booleanPreferencesKey("offline")
    private val retentionKey = booleanPreferencesKey("keep_raw_text")
    private val crashReportingKey = booleanPreferencesKey("crash_reporting")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val biometricKey = booleanPreferencesKey("biometric_unlock")
    private val pinFailuresKey = intPreferencesKey("pin_failures")
    private val pinLockedUntilKey = longPreferencesKey("pin_locked_until")
    private val data = store.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }
    val palette = data.map { prefs ->
        NotiflyPalette.entries.firstOrNull { it.name == prefs[paletteKey] } ?: NotiflyPalette.Evergreen
    }
    val themeMode = data.map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[themeModeKey] } ?: ThemeMode.SYSTEM }
    val onboardingComplete = data.map { it[onboardingKey] ?: false }
    val offline = data.map { it[offlineKey] ?: true }
    val keepRawText = data.map { it[retentionKey] ?: false }
    val crashReporting = data.map { it[crashReportingKey] ?: false }
    val pinSet = data.map { it[pinHashKey] != null }
    val biometricUnlock = data.map { it[pinHashKey] != null && (it[biometricKey] ?: false) }
    suspend fun setPalette(value: NotiflyPalette) { store.edit { it[paletteKey] = value.name } }
    suspend fun setThemeMode(value: ThemeMode) { store.edit { it[themeModeKey] = value.name } }
    suspend fun completeOnboarding() { store.edit { it[onboardingKey] = true } }
    suspend fun resetOnboarding() { store.edit { it[onboardingKey] = false } }
    suspend fun setOffline(value: Boolean) { store.edit { it[offlineKey] = value } }
    suspend fun setKeepRawText(value: Boolean) { store.edit { it[retentionKey] = value } }
    suspend fun setCrashReporting(value: Boolean) { store.edit { it[crashReportingKey] = value } }

    /** Stores only a salted PBKDF2 hash of [pin]; the PIN itself is never persisted. */
    suspend fun setPin(pin: String) {
        require(pin.length == PIN_LENGTH && pin.all(Char::isDigit)) { "PIN must be $PIN_LENGTH digits" }
        val salt = Random.nextBytes(16)
        val hash = withContext(Dispatchers.Default) { pbkdf2(pin, salt) }
        store.edit {
            it[pinSaltKey] = Base64.encode(salt)
            it[pinHashKey] = Base64.encode(hash)
            it.remove(pinFailuresKey)
            it.remove(pinLockedUntilKey)
        }
    }
    suspend fun clearPin() {
        store.edit {
            it.remove(pinHashKey)
            it.remove(pinSaltKey)
            it.remove(biometricKey)
            it.remove(pinFailuresKey)
            it.remove(pinLockedUntilKey)
        }
    }
    suspend fun setBiometricUnlock(value: Boolean) { store.edit { it[biometricKey] = value } }
    /** Checks [pin]; every [MAX_PIN_FAILURES] consecutive misses locks entry for [PIN_LOCKOUT]. */
    suspend fun verifyPin(pin: String, now: Instant = Clock.System.now()): PinResult {
        val prefs = data.first()
        val lockedUntil = prefs[pinLockedUntilKey] ?: 0L
        val nowMillis = now.toEpochMilliseconds()
        if (nowMillis < lockedUntil) return PinResult.LockedFor((lockedUntil - nowMillis + 999) / 1000)
        val hash = prefs[pinHashKey]?.let { Base64.decode(it) } ?: return PinResult.Ok
        val salt = Base64.decode(prefs[pinSaltKey] ?: return PinResult.Ok)
        val matches = withContext(Dispatchers.Default) { constantTimeEquals(pbkdf2(pin, salt), hash) }
        var locked = false
        store.edit {
            if (matches) { it.remove(pinFailuresKey); it.remove(pinLockedUntilKey); return@edit }
            val failures = (it[pinFailuresKey] ?: 0) + 1
            locked = failures >= MAX_PIN_FAILURES
            if (locked) { it.remove(pinFailuresKey); it[pinLockedUntilKey] = nowMillis + PIN_LOCKOUT.inWholeMilliseconds }
            else it[pinFailuresKey] = failures
        }
        return when {
            matches -> PinResult.Ok
            locked -> PinResult.LockedFor(PIN_LOCKOUT.inWholeSeconds)
            else -> PinResult.Wrong
        }
    }
}
