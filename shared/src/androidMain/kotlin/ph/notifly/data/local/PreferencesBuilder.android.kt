package ph.notifly.data.local

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.notiflyPreferences by preferencesDataStore("notifly")
fun appPreferences(context: Context) = AppPreferences(context.notiflyPreferences)
