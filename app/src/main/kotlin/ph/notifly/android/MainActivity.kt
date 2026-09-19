package ph.notifly.android

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import ph.notifly.android.service.NotificationCaptureService
import ph.notifly.data.local.AppDatabase
import ph.notifly.data.local.AllowedAppEntity
import ph.notifly.domain.source.TransactionSource
import ph.notifly.ui.NotiflyApp

class MainActivity : ComponentActivity() {
    private val source: TransactionSource by inject()
    private val installedApps: ph.notifly.data.local.InstalledApps by inject()
    private val available = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NotiflyApp(permissionAvailable = available.value, requestPermission = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
        }
    }
    override fun onResume() {
        super.onResume()
        available.value = source.isAvailable()
        if (available.value) NotificationListenerService.requestRebind(ComponentName(this, NotificationCaptureService::class.java))
        lifecycleScope.launch {
            try { installedApps.refresh() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { android.widget.Toast.makeText(this@MainActivity, "Couldn't refresh installed apps. Reopen Settings to retry.", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
}
