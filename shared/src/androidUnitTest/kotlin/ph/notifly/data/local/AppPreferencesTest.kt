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
    }

    @Test fun nonIoReadFailuresAreRethrown() {
        val preferences = AppPreferences(failingStore(IllegalStateException("broken store")))

        assertFailsWith<IllegalStateException> { runBlocking { preferences.palette.first() } }
    }
}
