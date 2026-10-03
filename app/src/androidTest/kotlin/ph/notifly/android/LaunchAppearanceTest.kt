package ph.notifly.android

import android.animation.ValueAnimator
import android.content.Intent
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import ph.notifly.data.local.AppPreferences
import ph.notifly.data.local.Appearance
import ph.notifly.ui.LaunchAnimationState
import ph.notifly.ui.AppSessionModel
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode

/** One emulator inspection batch; screenshots stay in test storage, never in app/network payloads. */
@RunWith(AndroidJUnit4::class)
class LaunchAppearanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences get() = GlobalContext.get().get<AppPreferences>()
    private var originalAppearance: Appearance? = null

    @Before fun requireCleanTestData() = runBlocking {
        // Never remove an existing user's PIN or onboarding state to prepare a test.
        assumeFalse("Run against isolated, unonboarded app data without a PIN",
            preferences.pinSet.first() || preferences.onboardingComplete.first())
        originalAppearance = preferences.appearance.first()
    }

    @After fun restoreTestData() = runBlocking {
        val original = originalAppearance ?: return@runBlocking
        preferences.clearPin()
        preferences.resetOnboarding()
        preferences.setPalette(original.palette)
        preferences.setThemeMode(original.themeMode)
    }

    private fun animatorScale() = android.provider.Settings.Global.getFloat(context.contentResolver, "animator_duration_scale", 1f)
    private fun setAnimatorScale(value: Float) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "settings put global animator_duration_scale $value",
        )).use { it.readBytes() }
    }
    private fun await(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!check() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue("Timed out waiting for launch/launcher state", check())
    }
    private fun state(scenario: ActivityScenario<MainActivity>): LaunchAnimationState {
        lateinit var state: LaunchAnimationState
        scenario.onActivity { state = ViewModelProvider(it)[LaunchViewModel::class.java].animation }
        return state
    }
    private fun capture(name: String, waitForIdle: Boolean = true) {
        if (waitForIdle) instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(context.filesDir, "splash-verification").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun selectedAlias() = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
    ).map { it.activityInfo.name }

    @Test fun persistedAppearanceLaunchRotationResumeAndLock() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val launch = state(scenario)
            await { launch.complete }
            capture("onboarding")
            scenario.recreate()
            assertSame(launch, state(scenario))
            assertTrue(state(scenario).complete)
            runBlocking { preferences.completeOnboarding() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val launch = state(scenario)
            await { launch.complete }
            for (palette in NotiflyPalette.entries) for (mode in ThemeMode.entries) {
                val selection = Appearance(palette, mode)
                runBlocking { preferences.setPalette(palette); preferences.setThemeMode(mode) }
                await { selectedAlias() == listOf(launcherAlias(selection)) }
                assertTrue("Appearance changes must not replay launch", launch.complete)
                Thread.sleep(150)
                capture("${palette.name}-${mode.name}")
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertSame(launch, state(scenario))
            assertTrue(launch.complete)
            scenario.recreate()
            assertSame(launch, state(scenario))
            capture("rotation-home")
        }
        runBlocking { preferences.setPin("123456") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { state(scenario).complete }
            capture("pin-lock")
            lateinit var session: AppSessionModel
            scenario.onActivity { session = ViewModelProvider(it)[AppSessionModel::class.java]; session.unlocked = true }
            scenario.recreate()
            scenario.onActivity { assertSame(session, ViewModelProvider(it)[AppSessionModel::class.java]) }
            assertTrue("Rotation must retain in-process authentication", session.unlocked)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertFalse("Backgrounding must relock", session.unlocked)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse("A fresh Activity must start locked", ViewModelProvider(it)[AppSessionModel::class.java].unlocked) }
        }
    }

    @Test fun removeAnimationsSkipsLaunch() {
        val scale = animatorScale()
        try {
            setAnimatorScale(0f)
            await { !ValueAnimator.areAnimatorsEnabled() }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await { state(scenario).complete }
                capture("remove-animations")
            }
        } finally { setAnimatorScale(scale) }
    }

    @Test fun rotationDuringTraceRetainsProgress() {
        val scale = animatorScale()
        try {
            // A slower system scale gives screenshot/recreation time without changing app durations.
            setAnimatorScale(5f)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val launch = state(scenario)
                await { launch.progress > 0f && !launch.complete }
                val progress = launch.progress
                capture("trace-in-progress", waitForIdle = false)
                assertFalse(launch.complete)
                scenario.recreate()
                assertSame(launch, state(scenario))
                assertTrue("The stroke must not restart", launch.progress >= progress)
                await { launch.complete }
            }
        } finally { setAnimatorScale(scale) }
    }

    @Test fun launcherAliasKeepsActivityDuringLiveIconChanges() {
        val original = runBlocking { preferences.appearance.first() }
        val alias = selectedAlias().single()
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(context.packageName, alias)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun resumed(): MainActivity? {
            var activity: MainActivity? = null
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull()
            }
            return activity
        }
        await { resumed()?.intent?.component?.className == MainActivity::class.java.name }
        val activity = resumed()!!
        try {
            val target = Appearance(NotiflyPalette.Evergreen, ThemeMode.LIGHT)
            runBlocking { preferences.setPalette(target.palette); preferences.setThemeMode(target.themeMode) }
            await { selectedAlias() == listOf(launcherAlias(target)) }
            Thread.sleep(500)
            assertSame(activity, resumed())
            assertFalse(activity.isFinishing)
        } finally {
            runBlocking { preferences.setPalette(original.palette); preferences.setThemeMode(original.themeMode) }
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
