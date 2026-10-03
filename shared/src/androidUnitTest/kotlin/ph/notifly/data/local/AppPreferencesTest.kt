package ph.notifly.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.IOException
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class AppPreferencesTest {
    private fun failingStore(error: Throwable) = object : DataStore<Preferences> {
        override val data = flow<Preferences> { throw error }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
            transform(emptyPreferences())
    }

    private fun memoryStore() = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }

    @Test fun appearanceAndHomeOrderSurviveReopeningStore() = runBlocking {
        val file = File.createTempFile("notifly", ".preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            // Okio supports atomic replacement on Windows as well as Android.
            val first = AppPreferences(PreferenceDataStoreFactory.create(scope = firstScope,
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() }))
            assertEquals(Appearance(), first.appearance.first())
            assertEquals(emptyList(), first.homeAccountOrder.first())
            first.setPalette(NotiflyPalette.Clay)
            first.setThemeMode(ThemeMode.DARK)
            first.setHomeAccountOrder(listOf(8L, 3L, 12L))
            firstScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            firstScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            val reopened = AppPreferences(PreferenceDataStoreFactory.create(scope = secondScope,
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() }))
            assertEquals(NotiflyPalette.Clay, reopened.palette.first())
            assertEquals(ThemeMode.DARK, reopened.themeMode.first())
            assertEquals(Appearance(NotiflyPalette.Clay, ThemeMode.DARK), reopened.appearance.first())
            assertEquals(listOf(8L, 3L, 12L), reopened.homeAccountOrder.first())
        } finally {
            firstScope.cancel()
            secondScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            secondScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            file.delete()
        }
    }

    @Test fun ioReadFailuresUseDefaults() = runBlocking {
        val preferences = AppPreferences(failingStore(IOException("read failed")))

        assertEquals(NotiflyPalette.Ube, preferences.palette.first())
        assertEquals(false, preferences.onboardingComplete.first())
        assertEquals(true, preferences.offline.first())
        assertEquals(false, preferences.keepRawText.first())
        assertEquals(false, preferences.crashReporting.first())
        assertEquals(emptyList(), preferences.homeAccountOrder.first())
    }

    @Test fun nonIoReadFailuresAreRethrown() {
        val preferences = AppPreferences(failingStore(IllegalStateException("broken store")))

        assertFailsWith<IllegalStateException> { runBlocking { preferences.palette.first() } }
    }

    @Test fun pinVerifiesLocksOutAndClearsBiometrics() = runBlocking {
        val prefs = AppPreferences(memoryStore())
        assertEquals(false, prefs.pinSet.first())
        assertFailsWith<IllegalArgumentException> { prefs.setPin("12a456") }
        prefs.setPin("123456")
        prefs.setBiometricUnlock(true)
        assertEquals(true, prefs.pinSet.first())
        assertEquals(true, prefs.biometricUnlock.first())
        assertEquals(PinResult.Ok, prefs.verifyPin("123456"))
        val now = kotlin.time.Clock.System.now()
        repeat(4) { assertEquals(PinResult.Wrong, prefs.verifyPin("000000", now)) }
        assertEquals(PinResult.LockedFor(30), prefs.verifyPin("000000", now))
        assertEquals(PinResult.LockedFor(30), prefs.verifyPin("123456", now))
        assertEquals(30L, prefs.lockoutSeconds(now))
        assertEquals(0L, prefs.lockoutSeconds(now + kotlin.time.Duration.parse("31s")))
        assertEquals(PinResult.Ok, prefs.verifyPin("123456", now + kotlin.time.Duration.parse("31s")))
        prefs.clearPin()
        assertEquals(false, prefs.pinSet.first())
        assertEquals(false, prefs.biometricUnlock.first())
    }
}
