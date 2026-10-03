package ph.notifly.android

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.PowerManager
import android.animation.ValueAnimator
import android.view.ViewTreeObserver
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ph.notifly.android.service.NotificationCaptureService
import ph.notifly.domain.source.TransactionSource
import ph.notifly.ui.NotiflyApp
import ph.notifly.ui.LaunchAnimationState


class LaunchViewModel : ViewModel() {
    val animation = LaunchAnimationState()
}

class MainActivity : ComponentActivity() {
    private val source: TransactionSource by inject()
    private val installedApps: ph.notifly.data.local.InstalledApps by inject()
    private val available = mutableStateOf(false)
    private val batteryExempt = mutableStateOf(false)
    private val biometricAvailable = mutableStateOf(false)
    private val animationsEnabled = mutableStateOf(true)
    private val nativeSplashReleased = mutableStateOf(false)
    private var ready = false
    private var preDraw: ViewTreeObserver.OnPreDrawListener? = null
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep the task rooted in the enabled activity. Disabling the alias used to launch a
        // task otherwise makes Android finish that task even with DONT_KILL_APP.
        val activity = ComponentName(this, MainActivity::class.java)
        if (intent.component != activity) {
            startActivity(Intent(intent).setComponent(activity).addFlags(
                if (isTaskRoot) Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                else Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ))
            finish()
            return
        }
        val launch = ViewModelProvider(this)[LaunchViewModel::class.java].animation
        nativeSplashReleased.value = lastNonConfigurationInstance != null
        // Process restoration is a fresh launch; only an in-process configuration change retains completion.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash ->
                splash.remove()
                nativeSplashReleased.value = true
            }
        }
        val content = findViewById<android.view.View>(android.R.id.content)
        preDraw = ViewTreeObserver.OnPreDrawListener {
            if (ready) {
                preDraw?.let { content.viewTreeObserver.removeOnPreDrawListener(it) }
                preDraw = null
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) nativeSplashReleased.value = true
            }
            ready
        }.also { content.viewTreeObserver.addOnPreDrawListener(it) }

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
                launchAnimation = launch,
                startLaunchAnimation = nativeSplashReleased.value,
                animationsEnabled = animationsEnabled.value,
                onReady = { ready = true },
            )
        }
    }
    override fun onResume() {
        super.onResume()
        animationsEnabled.value = ValueAnimator.areAnimatorsEnabled()
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
    override fun onDestroy() {
        preDraw?.let { findViewById<android.view.View>(android.R.id.content).viewTreeObserver.removeOnPreDrawListener(it) }
        preDraw = null
        super.onDestroy()
    }
}
