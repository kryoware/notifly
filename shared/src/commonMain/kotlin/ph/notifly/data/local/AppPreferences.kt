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
private const val CATEGORY_BUDGET_PREFIX = "category_budget_minor:"
private const val ACCOUNT_BALANCE_PREFIX = "account_balance:"

/** A balance the user typed in for a finance app; transactions after [setAt] are applied on top of it. */
data class ManualBalance(val minor: Long, val setAt: Instant)

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
    private val monthlyBudgetKey = longPreferencesKey("monthly_budget_minor")
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
    val monthlyBudget = data.map { it[monthlyBudgetKey] }
    /** Monthly limit per spending category, keyed by category name. */
    val categoryBudgets = data.map { prefs ->
        prefs.asMap().entries.filter { it.key.name.startsWith(CATEGORY_BUDGET_PREFIX) }
            .associate { it.key.name.removePrefix(CATEGORY_BUDGET_PREFIX) to it.value as Long }
    }
    /** Keyed by package name; each value is stored as `minor@epochMillis`. */
    val accountBalances = data.map { prefs ->
        prefs.asMap().entries.filter { it.key.name.startsWith(ACCOUNT_BALANCE_PREFIX) }.mapNotNull { (key, value) ->
            val (minor, at) = (value as String).split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
            key.name.removePrefix(ACCOUNT_BALANCE_PREFIX) to
                ManualBalance(minor.toLongOrNull() ?: return@mapNotNull null, Instant.fromEpochMilliseconds(at.toLongOrNull() ?: return@mapNotNull null))
        }.toMap()
    }
    suspend fun setPalette(value: NotiflyPalette) { store.edit { it[paletteKey] = value.name } }
    suspend fun setThemeMode(value: ThemeMode) { store.edit { it[themeModeKey] = value.name } }
    suspend fun completeOnboarding() { store.edit { it[onboardingKey] = true } }
    suspend fun resetOnboarding() { store.edit { it[onboardingKey] = false } }
    suspend fun setOffline(value: Boolean) { store.edit { it[offlineKey] = value } }
    suspend fun setKeepRawText(value: Boolean) { store.edit { it[retentionKey] = value } }
    suspend fun setCrashReporting(value: Boolean) { store.edit { it[crashReportingKey] = value } }
    /** Stores the monthly limit in minor units, or removes it when [minor] is null. */
    suspend fun setMonthlyBudget(minor: Long?) { store.edit { if (minor == null) it.remove(monthlyBudgetKey) else it[monthlyBudgetKey] = minor } }
    /** Stores a balance in minor units and its cutoff time for [packageName], or removes it when null. */
    suspend fun setAccountBalance(packageName: String, balance: ManualBalance?) {
        val key = stringPreferencesKey(ACCOUNT_BALANCE_PREFIX + packageName)
        store.edit { if (balance == null) it.remove(key) else it[key] = "${balance.minor}@${balance.setAt.toEpochMilliseconds()}" }
    }
    /** Stores a monthly limit in minor units for the exact category name, or removes it when null. */
    suspend fun setCategoryBudget(category: String, minor: Long?) {
        val key = longPreferencesKey(CATEGORY_BUDGET_PREFIX + category)
        store.edit { if (minor == null) it.remove(key) else it[key] = minor }
    }

    /**
     * Stores only a salted PBKDF2 hash of [pin]; the PIN itself is never persisted.
     * Resets failed attempts and lockout. Hashing and preference-write failures propagate.
     *
     * @throws IllegalArgumentException if [pin] is not exactly [PIN_LENGTH] digits.
     */
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
    /** Removes the PIN, biometric opt-in, failed attempts, and lockout; storage failures propagate. */
    suspend fun clearPin() {
        store.edit {
            it.remove(pinHashKey)
            it.remove(pinSaltKey)
            it.remove(biometricKey)
            it.remove(pinFailuresKey)
            it.remove(pinLockedUntilKey)
        }
    }
    /** Stores the biometric opt-in; [biometricUnlock] remains false until a PIN hash is present. */
    suspend fun setBiometricUnlock(value: Boolean) { store.edit { it[biometricKey] = value } }
    /**
     * Checks [pin]; every [MAX_PIN_FAILURES] consecutive misses locks entry for [PIN_LOCKOUT].
     * [now] determines lockout expiry; remaining seconds are rounded up. A match clears failures.
     * Without an active lockout, returns [PinResult.Ok] if the hash or salt is absent.
     * A caught read [IOException] uses empty preferences and also returns Ok.
     * Other read failures, hashing failures, and preference-write failures propagate.
     *
     * @throws IllegalArgumentException if a stored hash or salt cannot be decoded from Base64.
     */
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
