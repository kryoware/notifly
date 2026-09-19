package ph.notifly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun NotiflyApp(demo: Boolean = false, permissionAvailable: Boolean = false, requestPermission: () -> Unit = {}) {
    val preferences = koinInject<AppPreferences>()
    val database = koinInject<ph.notifly.data.local.AppDatabase>()
    val realTransactions = koinInject<TransactionRepository>()
    val realApps = koinInject<AllowListRepository>()
    val realCaptures = koinInject<CaptureRepository>()
    val captures = remember(demo) { if (demo) DemoCaptures() else realCaptures }
    val transactions = remember(demo) { if (demo) DemoTransactions() else realTransactions }
    val apps = remember(demo) { if (demo) DemoAllowList() else realApps }
    val palette by preferences.palette.collectAsState(NotiflyPalette.Evergreen)
    val onboarded by preferences.onboardingComplete.collectAsState(null)
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(target: String) {
        nav.navigate(target) {
            launchSingleTop = true
            if (target in listOf("home", "transactions", "settings")) popUpTo(nav.graph.id) { inclusive = false }
        }
    }
    val handle: (UiEvent) -> Unit = { event -> when (event) {
        is UiEvent.Navigate -> navigate(event.route)
        is UiEvent.Message -> { if (event.undo != null) navigate("transactions"); scope.launch {
            if (snackbar.showSnackbar(event.text, actionLabel = event.undo?.let { "Undo" }) == SnackbarResult.ActionPerformed) {
                try { event.undo?.let { transactions.upsert(it) } }
                catch (_: Exception) { snackbar.showSnackbar("Couldn't restore the transaction. Please try again.") }
            }
        }; Unit }
    } }
    NotiflyTheme(palette) {
        if (onboarded == null) { CircularProgressIndicator(); return@NotiflyTheme }
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (demo) "Notifly · Demo" else "Notifly") }, navigationIcon = {
                if (route !in listOf("home", "transactions", "settings", "onboarding")) IconButton(onClick = { if (!nav.popBackStack()) navigate("home") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }) },
            bottomBar = {
                if (route in listOf("home", "transactions", "settings")) NavigationBar {
                    listOf(Triple("home", "Home", Icons.Default.Home), Triple("transactions", "Transactions", Icons.AutoMirrored.Filled.List), Triple("settings", "Settings", Icons.Default.Settings)).forEach { (target, label, icon) ->
                        NavigationBarItem(selected = route == target, onClick = { navigate(target) }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(nav, startDestination = if (onboarded == true) "home" else "onboarding", modifier = Modifier.fillMaxSize().padding(padding)) {
                composable("onboarding") { val m = viewModel { OnboardingModel() }; Events(m, handle); OnboardingScreen(m, requestPermission, permissionAvailable) }
                composable("auth") { val m = viewModel { AuthModel(preferences, demo) }; Events(m, handle); AuthScreen(m, demo) }
                composable("home") { val m = viewModel { HomeModel(transactions) }; Events(m, handle); HomeScreen(m) }
                composable("transactions") { val m = viewModel { TransactionsModel(transactions) }; Events(m, handle); TransactionsScreen(m) }
                composable("settings") { val m = viewModel { SettingsModel(preferences, database.transactionDao().observePendingCount()) }; Events(m, handle); SettingsScreen(m, permissionAvailable, requestPermission) }
                composable("allow-list") { val m = viewModel { AllowListModel(apps) }; Events(m, handle); AllowListScreen(m) }
                composable("choose-apps") { val m = viewModel { AllowListModel(apps) }; Events(m, handle); AllowListScreen(m, onboarding = true) }
                composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    val m = viewModel { EditorModel(transactions, it.arguments?.getLong("id") ?: 0L) }; Events(m, handle); EditorScreen(m)
                }
                composable("themes") { ThemeGallery(preferences) }
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
