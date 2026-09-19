package ph.notifly.android

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ph.notifly.android.service.NotificationCaptureService
import ph.notifly.domain.source.TransactionSource
import ph.notifly.ui.NotiflyApp

class MainActivity : ComponentActivity() {
    private val source: TransactionSource by inject()
    private val installedApps: ph.notifly.data.local.InstalledApps by inject()
    private val available = mutableStateOf(false)
    private val batteryExempt = mutableStateOf(false)
    private fun openSystemSettings(action: String) {
        try { startActivity(Intent(action)) }
        catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "This device has no such settings screen.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NotiflyApp(
                permissionAvailable = available.value,
                requestPermission = { openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                batteryExempt = batteryExempt.value,
                requestBatteryExemption = { openSystemSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            )
        }
    }
    override fun onResume() {
        super.onResume()
        available.value = source.isAvailable()
        batteryExempt.value = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (available.value) NotificationListenerService.requestRebind(ComponentName(this, NotificationCaptureService::class.java))
        lifecycleScope.launch {
            try { installedApps.refresh() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { android.widget.Toast.makeText(this@MainActivity, "Couldn't refresh installed apps. Reopen Settings to retry.", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
}
