package ph.notifly.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.IOException

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

    @Test fun paletteSurvivesReopeningStore() = runBlocking {
        val file = File.createTempFile("notifly", ".preferences_pb")
        file.delete()
        val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val first = AppPreferences(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            assertEquals(NotiflyPalette.Evergreen, first.palette.first())
            first.setPalette(NotiflyPalette.Clay)
            firstScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            firstScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            val reopened = AppPreferences(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(NotiflyPalette.Clay, reopened.palette.first())
        } finally {
            firstScope.cancel()
            secondScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            secondScope.coroutineContext[kotlinx.coroutines.Job]!!.join()
            file.delete()
        }
    }

    @Test fun ioReadFailuresUseDefaults() = runBlocking {
        val preferences = AppPreferences(failingStore(IOException("read failed")))

        assertEquals(NotiflyPalette.Evergreen, preferences.palette.first())
        assertEquals(false, preferences.onboardingComplete.first())
        assertEquals(true, preferences.offline.first())
        assertEquals(false, preferences.keepRawText.first())
        assertEquals(false, preferences.crashReporting.first())
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
        assertEquals(PinResult.Ok, prefs.verifyPin("123456", now + kotlin.time.Duration.parse("31s")))
        prefs.clearPin()
        assertEquals(false, prefs.pinSet.first())
        assertEquals(false, prefs.biometricUnlock.first())
    }
}
