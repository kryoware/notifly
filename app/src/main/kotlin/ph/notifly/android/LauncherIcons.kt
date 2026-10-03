package ph.notifly.android

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import ph.notifly.data.local.Appearance
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode

internal fun launcherAlias(appearance: Appearance) = "ph.notifly.android.Launcher${appearance.palette.name}${appearance.themeMode.name.lowercase().replaceFirstChar(Char::titlecase)}"

internal class LauncherIcons(private val packageManager: PackageManager, private val packageName: String) {
    private val aliases = NotiflyPalette.entries.flatMap { palette ->
        ThemeMode.entries.map { mode -> ComponentName(packageName, launcherAlias(Appearance(palette, mode))) }
    }
    private val default = ComponentName(packageName, launcherAlias(Appearance()))

    private fun enabled(component: ComponentName) = when (packageManager.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> component == default
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        else -> false
    }

    /** Reconcile actual PM state on every process start, including an interrupted previous switch. */
    fun apply(appearance: Appearance): Boolean {
        val target = ComponentName(packageName, launcherAlias(appearance))
        return try {
            val changes = aliases.filter { enabled(it) != (it == target) }
            if (changes.isEmpty()) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.setComponentEnabledSettings(changes.map {
                    PackageManager.ComponentEnabledSetting(it,
                        if (it == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP)
                })
            } else {
                // Keep a working entry even if any later disabling operation fails.
                if (!enabled(target)) packageManager.setComponentEnabledSetting(target,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                aliases.filter { it != target && enabled(it) }.forEach {
                    packageManager.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
            true
        } catch (_: RuntimeException) {
            // A batch failure is atomic; fallback failures leave the old or replacement entry enabled.
            // Reconciliation runs again on the next launch (or appearance change).
            false
        }
    }
}
