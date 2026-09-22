package ph.notifly.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.repository.*
import ph.notifly.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotiflyApp(
    demo: Boolean = false,
    permissionAvailable: Boolean = false,
    requestPermission: () -> Unit = {},
    batteryExempt: Boolean = false,
    requestBatteryExemption: () -> Unit = {},
    versionName: String = "",
    isDebugBuild: Boolean = false,
) {
    val preferences = koinInject<AppPreferences>()
    val database = koinInject<ph.notifly.data.local.AppDatabase>()
    val realTransactions = koinInject<TransactionRepository>()
    val realApps = koinInject<AllowListRepository>()
    val realCaptures = koinInject<CaptureRepository>()
    val captures = remember(demo) { if (demo) DemoCaptures() else realCaptures }
    val transactions = remember(demo) { if (demo) DemoTransactions() else realTransactions }
    val apps = remember(demo) { if (demo) DemoAllowList() else realApps }
    val appLabels by apps.observeAll().collectAsState(emptyList())
    val palette by preferences.palette.collectAsState(NotiflyPalette.Evergreen)
    val themeMode by preferences.themeMode.collectAsState(ThemeMode.SYSTEM)
    val onboarded by preferences.onboardingComplete.collectAsState(null)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(target: String) {
        if (route == target) return
        nav.navigate(target) {
            launchSingleTop = true
            if (target in listOf("home", "transactions", "insights", "settings")) popUpTo(nav.graph.id) { inclusive = false }
        }
    }
    val handle: (UiEvent) -> Unit = { event -> when (event) {
        is UiEvent.Navigate -> navigate(event.route)
        is UiEvent.Message -> { if (event.undo != null) navigate("transactions"); scope.launch {
            if (snackbar.showSnackbar(event.text, actionLabel = event.undo?.let { "Undo" }) == SnackbarResult.ActionPerformed) {
                try { event.undo?.let { transactions.upsert(it) } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { snackbar.showSnackbar("Couldn't restore the transaction. Please try again.") }
            }
        }; Unit }
    } }
    val topLevel = listOf("home", "transactions", "insights", "settings")
    val title = when {
        route == "home" -> "Notifly"
        route == "transactions" -> "Transactions"
        route == "insights" -> "Insights"
        route == "settings" -> "Settings"
        route?.startsWith("edit/") == true -> if (route == "edit/0") "Add transaction" else "Edit transaction"
        route?.startsWith("from-log/") == true -> "Add transaction"
        route == "allow-list" || route == "choose-apps" -> "Allowed apps"
        route == "log" -> "Notification log"
        route == "themes" -> "Theme palettes"
        else -> "Notifly"
    }
    NotiflyTheme(palette, themeMode) {
        if (onboarded == null) {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            return@NotiflyTheme
        }
        Scaffold(
            topBar = {
                if (route != "onboarding" && route != "auth") {
                    TopAppBar(title = { Text(if (route in topLevel && demo) "$title · Demo" else title) }, navigationIcon = {
                        if (route !in topLevel) IconButton(onClick = { if (!nav.popBackStack()) navigate("home") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    })
                }
            },
            bottomBar = {
                if (route in topLevel) NavigationBar {
                    listOf(Triple("home", "Home", Icons.Default.Home), Triple("transactions", "Transactions", Icons.AutoMirrored.Filled.List), Triple("insights", "Insights", Icons.Default.PieChart), Triple("settings", "Settings", Icons.Default.Settings)).forEach { (target, label, icon) ->
                        NavigationBarItem(selected = route == target, onClick = { navigate(target) }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(
                nav, startDestination = if (onboarded == true) "home" else "onboarding",
                modifier = Modifier.fillMaxSize().padding(padding),
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(220)) + androidx.compose.animation.fadeIn(tween(220))
                },
                exitTransition = { androidx.compose.animation.fadeOut(tween(120)) },
                popEnterTransition = { androidx.compose.animation.fadeIn(tween(220)) },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(220)) + androidx.compose.animation.fadeOut(tween(120))
                },
            ) {
                composable("onboarding") { val m = viewModel { OnboardingModel() }; Events(m, handle); OnboardingScreen(m, requestPermission, permissionAvailable, batteryExempt, requestBatteryExemption) }
                composable("auth") { val m = viewModel { AuthModel(preferences, demo) }; Events(m, handle); AuthScreen(m, demo) }
                composable("home") { val m = viewModel { HomeModel(transactions) }; Events(m, handle); HomeScreen(m, appLabels.associate { it.packageName to it.label }) }
                composable("insights") { val m = viewModel { InsightsModel(transactions) }; Events(m, handle); InsightsScreen(m) }
                composable("transactions") { val m = viewModel { TransactionsModel(transactions) }; Events(m, handle); TransactionsScreen(m, appLabels.associate { it.packageName to it.label }) }
                composable("settings") { val m = viewModel { SettingsModel(preferences, database.transactionDao().observePendingCount()) }; Events(m, handle); SettingsScreen(m, permissionAvailable, requestPermission, versionName, isDebugBuild) }
                composable("allow-list") { val m = viewModel { AllowListModel(apps) }; Events(m, handle); AllowListScreen(m) }
                composable("choose-apps") { val m = viewModel { AllowListModel(apps) }; Events(m, handle); AllowListScreen(m, onboarding = true) }
                composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    val m = viewModel { EditorModel(transactions, it.arguments?.getLong("id") ?: 0L) }; Events(m, handle); EditorScreen(m)
                }
                composable("themes") { val m = viewModel { SettingsModel(preferences, database.transactionDao().observePendingCount()) }; Events(m, handle); ThemeGallery(m) }
                composable("log") { val m = viewModel { LogModel(captures, preferences) }; Events(m, handle); LogScreen(m) }
                composable("from-log/{captureId}", arguments = listOf(navArgument("captureId") { type = NavType.LongType })) {
                    val m = viewModel { EditorModel(transactions, 0L, captures, it.arguments?.getLong("captureId")) }; Events(m, handle); EditorScreen(m)
                }
            }
        }
    }
}

@Composable
private fun Events(model: ScreenModel, handle: (UiEvent) -> Unit) {
    val current by rememberUpdatedState(handle)
    LaunchedEffect(model) { model.events.collect { current(it) } }
}
