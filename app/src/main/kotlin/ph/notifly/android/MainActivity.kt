package ph.notifly.android

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
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
    private val launchRoute = mutableStateOf<String?>(null)
    private val launchRouteKey = mutableIntStateOf(0)
    private val notificationsAllowed = mutableStateOf(true)
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        notificationsAllowed.value = it
    }
    private fun refreshNotificationsAllowed() {
        notificationsAllowed.value = getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
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
        // Keep the task rooted in the enabled activity. Disabling the alias used to launch a
        // task otherwise makes Android finish that task even with DONT_KILL_APP.
        val activity = ComponentName(this, MainActivity::class.java)
        if (intent.component != activity) {
            if (isTaskRoot) {
                val existing = getSystemService(android.app.ActivityManager::class.java).appTasks.firstOrNull {
                    it.taskInfo?.baseIntent?.component == activity
                }
                if (existing != null) existing.moveToFront()
                else startActivity(Intent(intent).setComponent(activity).setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                ))
                // A separate task is necessary: CLEAR_TASK retains the old alias's task identity.
                finishAndRemoveTask()
            } else {
                startActivity(Intent(intent).setComponent(activity).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ))
                finish()
            }
            return
        }
        val launch = ViewModelProvider(this)[LaunchViewModel::class.java].animation
        (application as NotiflyApplication).reconcileLauncherIcon()
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
        if (savedInstanceState == null) launchRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        refreshNotificationsAllowed()

        setContent {
            NotiflyApp(
                permissionAvailable = available.value,
                requestPermission = { openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                batteryExempt = batteryExempt.value,
                requestBatteryExemption = { openSystemSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                versionName = BuildConfig.VERSION_NAME,
                buildLabel = "${BuildConfig.BUILD_TYPE}-${BuildConfig.SOURCE_REF}-${BuildConfig.SOURCE_SHA}",
                isDebugBuild = BuildConfig.DEBUG,
                biometricAvailable = biometricAvailable.value,
                authenticateBiometric = ::authenticate,
                launchAnimation = launch,
                startLaunchAnimation = nativeSplashReleased.value,
                animationsEnabled = animationsEnabled.value,
                onReady = { ready = true },
                shouldLockOnStop = { !isChangingConfigurations },
                notificationsAllowed = notificationsAllowed.value,
                requestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                },
                launchRoute = launchRoute.value,
                launchRouteKey = launchRouteKey.intValue,
                onDarkChanged = { dark -> WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } },
            )
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        launchRouteKey.intValue++
    }
    private fun tryRebindNotificationListener() {
        if (!available.value || source.connection.value == "Connected") return
        val component = ComponentName(this, NotificationCaptureService::class.java)
        runCatching {
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        runCatching {
            NotificationListenerService.requestRebind(component)
        }
    }
    override fun onResume() {
        super.onResume()
        animationsEnabled.value = ValueAnimator.areAnimatorsEnabled()
        refreshNotificationsAllowed()
        available.value = source.isAvailable()
        batteryExempt.value = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        biometricAvailable.value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            getSystemService(BiometricManager::class.java).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        tryRebindNotificationListener()
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
    companion object { const val EXTRA_ROUTE = "route" }
}
