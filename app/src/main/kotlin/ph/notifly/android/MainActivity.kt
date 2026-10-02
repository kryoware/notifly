package ph.notifly.android

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    private val biometricAvailable = mutableStateOf(false)
    private val launchRoute = mutableStateOf<String?>(null)
    private val launchRouteKey = mutableIntStateOf(0)
    private val notificationsAllowed = mutableStateOf(true)
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        notificationsAllowed.value = it
    }
    private fun refreshNotificationsAllowed() {
        notificationsAllowed.value = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    // ponytail: framework BiometricPrompt needs API 30 for BIOMETRIC_STRONG; API 26–29 get PIN only. androidx.biometric if older devices matter.
    /**
     * Prompts for strong biometrics on API 30+, calling [onSuccess] only after authentication succeeds.
     * Earlier APIs do nothing; cancellation and unsuccessful authentication do not invoke the callback.
     */
    private fun authenticate(onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        BiometricPrompt.Builder(this)
            .setTitle("Unlock Notifly")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton("Use PIN", mainExecutor) { _, _ -> }
            .build()
            .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            })
    }
    private fun openSystemSettings(action: String) {
        try { startActivity(Intent(action)) }
        catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "This device has no such settings screen.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        if (resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launchRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        refreshNotificationsAllowed()

        setContent {
            NotiflyApp(
                permissionAvailable = available.value,
                requestPermission = { openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                batteryExempt = batteryExempt.value,
                requestBatteryExemption = { openSystemSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                versionName = BuildConfig.VERSION_NAME,
                isDebugBuild = BuildConfig.DEBUG,
                biometricAvailable = biometricAvailable.value,
                authenticateBiometric = ::authenticate,
                notificationsAllowed = notificationsAllowed.value,
                requestNotifications = { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                launchRoute = launchRoute.value,
                launchRouteKey = launchRouteKey.intValue,
            )
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        launchRouteKey.intValue++
    }
    override fun onResume() {
        super.onResume()
        refreshNotificationsAllowed()
        available.value = source.isAvailable()
        batteryExempt.value = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        biometricAvailable.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            getSystemService(BiometricManager::class.java).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (available.value) NotificationListenerService.requestRebind(ComponentName(this, NotificationCaptureService::class.java))
        lifecycleScope.launch {
            try { installedApps.refresh() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { android.widget.Toast.makeText(this@MainActivity, "Couldn't refresh installed apps. Reopen Settings to retry.", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    companion object { const val EXTRA_ROUTE = "route" }
}
