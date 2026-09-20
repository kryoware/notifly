package ph.notifly.data.local

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledApps(private val context: Context, private val database: AppDatabase) {
    /** Discovers other non-system and launchable system apps without overwriting existing allow-list choices. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val manager = context.packageManager
        val apps = manager.getInstalledApplications(0).filter {
            it.packageName != context.packageName &&
                (it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || manager.getLaunchIntentForPackage(it.packageName) != null)
        }.map { AllowedAppEntity(it.packageName, manager.getApplicationLabel(it).toString(), "Installed app", false) }
        database.allowedAppDao().addInstalled(apps)
    }
}
