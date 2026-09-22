package ph.notifly.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun appPreferences(): AppPreferences {
    val directory = requireNotNull(NSFileManager.defaultManager.URLForDirectory(
        NSDocumentDirectory, NSUserDomainMask, null, true, null,
    )).path
    return AppPreferences(PreferenceDataStoreFactory.createWithPath {
        "$directory/notifly.preferences_pb".toPath()
    })
}
