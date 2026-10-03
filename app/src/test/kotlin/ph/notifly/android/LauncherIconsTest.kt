package ph.notifly.android

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowApplicationPackageManager
import ph.notifly.data.local.Appearance
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Run the same contract against the pre-33 sequential path and API 33 batch path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 33], application = Application::class)
class LauncherIconsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val pm = context.packageManager
    private val launcher = LauncherIcons(pm, context.packageName)
    private val selections = NotiflyPalette.entries.flatMap { palette -> ThemeMode.entries.map { Appearance(palette, it) } }
    private fun component(selection: Appearance) = ComponentName(context.packageName, launcherAlias(selection))
    private fun active() = selections.filter {
        when (pm.getComponentEnabledSetting(component(it))) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> pm.getActivityInfo(component(it), PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
    }

    @Test fun allMappingsExistAndExactlyOneLauncherSurvivesEverySelection() {
        assertEquals(12, selections.map(::launcherAlias).toSet().size)
        assertEquals(listOf(Appearance()), active())
        selections.forEach { selection ->
            val info = pm.getActivityInfo(component(selection), PackageManager.MATCH_DISABLED_COMPONENTS)
            assertEquals("ph.notifly.android.MainActivity", info.targetActivity)
            assertTrue(info.icon != 0)
            assertTrue(launcher.apply(selection))
            assertEquals(listOf(selection), active())
        }
        val main = ComponentName(context, MainActivity::class.java)
        assertTrue(pm.getActivityInfo(main, 0).enabled)
        val entries = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        assertEquals(1, entries.size)
        assertEquals(launcherAlias(selections.last()), entries.single().activityInfo.name)
    }

    @Test fun unchangedDefaultAndRepeatedApplicationDoNotRewriteState() {
        assertTrue(launcher.apply(Appearance()))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(component(Appearance())))
        val target = Appearance(NotiflyPalette.Clay, ThemeMode.DARK)
        assertTrue(launcher.apply(target))
        val before = selections.map { pm.getComponentEnabledSetting(component(it)) }
        repeat(3) { assertTrue(launcher.apply(target)) }
        assertEquals(before, selections.map { pm.getComponentEnabledSetting(component(it)) })
        assertEquals(listOf(target), active())
    }

    @Test fun nextLaunchReconcilesAnInterruptedFallbackWithTwoEnabledEntries() {
        val target = Appearance(NotiflyPalette.Slate, ThemeMode.LIGHT)
        pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        assertEquals(2, active().size)
        assertTrue(launcher.apply(target))
        assertEquals(listOf(target), active())
    }

    @Test
    @Config(sdk = [32], shadows = [RecordingPackageManager::class])
    fun fallbackEnablesReplacementFirstAndKeepsItOnFailureThenRetries() {
        val shadow = Shadow.extract<RecordingPackageManager>(pm)
        val target = Appearance(NotiflyPalette.Evergreen, ThemeMode.DARK)
        shadow.failDisabling = true
        assertEquals(false, launcher.apply(target))
        assertEquals(component(target), shadow.calls.first().first)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, shadow.calls.first().second)
        assertEquals(2, active().size)
        shadow.failDisabling = false
        assertTrue(launcher.apply(target))
        assertEquals(listOf(target), active())
        val count = shadow.calls.size
        assertTrue(launcher.apply(target))
        assertEquals(count, shadow.calls.size)
    }
}

@Implements(className = "android.app.ApplicationPackageManager")
class RecordingPackageManager : ShadowApplicationPackageManager() {
    val calls = mutableListOf<Pair<ComponentName, Int>>()
    var failDisabling = false

    @Implementation
    public override fun setComponentEnabledSetting(component: ComponentName, state: Int, flags: Int) {
        if (!component.className.startsWith("ph.notifly.android.Launcher")) {
            super.setComponentEnabledSetting(component, state, flags)
            return
        }
        assertEquals(PackageManager.DONT_KILL_APP, flags)
        calls += component to state
        if (failDisabling && state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) throw SecurityException("Test failure")
        super.setComponentEnabledSetting(component, state, flags)
    }
}
