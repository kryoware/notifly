package ph.notifly.ui

import org.jetbrains.compose.resources.painterResource
import notifly.shared.generated.resources.Res
import notifly.shared.generated.resources.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.repository.*
import ph.notifly.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
fun NotiflyApp(
    demo: Boolean = false,
    permissionAvailable: Boolean = false,
    requestPermission: () -> Unit = {},
    batteryExempt: Boolean = false,
    requestBatteryExemption: () -> Unit = {},
    versionName: String = "",
    isDebugBuild: Boolean = false,
    biometricAvailable: Boolean = false,
    authenticateBiometric: (onSuccess: () -> Unit) -> Unit = {},
) {
    val preferences = koinInject<AppPreferences>()
    val database = koinInject<ph.notifly.data.local.AppDatabase>()
    val realTransactions = koinInject<TransactionRepository>()
    val realApps = koinInject<AllowListRepository>()
    val realLedger = koinInject<LedgerRepository>()
    val realCaptures = koinInject<CaptureRepository>()
    var demo by rememberSaveable { mutableStateOf(demo) }
    val captures = remember(demo) { if (demo) DemoCaptures() else realCaptures }
    val transactions = remember(demo) { if (demo) DemoTransactions(demoRows()) else realTransactions }
    val ledger = remember(demo) { if (demo) DemoLedger(transactions) else realLedger }
    val apps = remember(demo) { if (demo) DemoAllowList() else realApps }
    val allowed by apps.observeAll().collectAsState(emptyList())
    val appLabels = remember(allowed) { allowed.associate { it.packageName to it.label } }
    val palette by preferences.palette.collectAsState(NotiflyPalette.Ube)
    val themeMode by preferences.themeMode.collectAsState(ThemeMode.SYSTEM)
    val onboarded by preferences.onboardingComplete.collectAsState(null)
    val pinSet by preferences.pinSet.collectAsState(null)
    val biometricUnlock by preferences.biometricUnlock.collectAsState(false)
    var unlocked by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }
    // A new controller per mode, so no screen keeps a ViewModel bound to the other mode's repositories.
    val nav = key(demo) { rememberNavController() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun navigate(target: String) {
        if (route == target) return
        if ((target == "accounts" && route == "account/{id}") ||
            (target == "categories" && route == "category/{id}/{type}")) {
            if (nav.popBackStack(target, inclusive = false)) return
        }
        nav.navigate(target) {
            launchSingleTop = true
            if (target in listOf("home", "transactions", "insights", "settings")) popUpTo(nav.graph.id) { inclusive = false }
        }
    }
    val handle: (UiEvent) -> Unit = { event -> when (event) {
        is UiEvent.Navigate -> navigate(event.route)
        is UiEvent.Message -> { if (event.undo != null) navigate("transactions"); scope.launch {
            if (snackbar.showSnackbar(event.text, actionLabel = event.undo?.let { "Undo" }, duration = if (event.undo != null) SnackbarDuration.Long else SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                try { event.undo?.let { transactions.upsert(it) } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { snackbar.showSnackbar("Couldn't restore the transaction. Please try again.") }
            }
        }; Unit }
    } }
    val topLevel = listOf("home", "transactions", "insights", "settings")
    NotiflyTheme(palette, themeMode) {
        if (onboarded == null || pinSet == null) {
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            return@NotiflyTheme
        }
        val navigationSuiteType = if (route in topLevel) {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        } else {
            NavigationSuiteType.None
        }
        val destinations: @Composable (Modifier) -> Unit = { navModifier ->
            NavHost(
                nav, startDestination = if (onboarded == true || demo) "home" else "onboarding/0",
                modifier = navModifier,
            ) {
                // One route per page, so system and predictive Back step back through the flow.
                composable("onboarding/{page}", arguments = listOf(navArgument("page") { type = NavType.IntType })) {
                    val m = viewModel { OnboardingModel(preferences) }; Events(m, handle)
                    OnboardingScreen(m, it.arguments?.read { getInt("page") } ?: 0, requestPermission, permissionAvailable, batteryExempt, requestBatteryExemption)
                }
                composable("auth") { val m = viewModel { AuthModel(preferences, demo) }; Events(m, handle); AuthScreen(m, demo) }
                composable("home") { val m = viewModel { HomeModel(transactions, apps, preferences, ledger) }; Events(m, handle); HomeScreen(m, appLabels, snackbar, demo,
                    allowed.filter { it.listening }.map { it.label }, permissionAvailable, requestPermission) }
                composable("insights") { val m = viewModel { InsightsModel(transactions, preferences, ledger) }; Events(m, handle)
                    AppDestination(if (demo) "Insights · Demo" else "Insights", snackbar) { InsightsScreen(m, appLabels) } }
                composable("transactions") { val m = viewModel { TransactionsModel(transactions, ledger) }; Events(m, handle); TransactionsScreen(m, appLabels, snackbar, demo) }
                composable("settings") { val m = viewModel { SettingsModel(preferences, database.transactionDao().observePendingCount()) }; Events(m, handle)
                    AppDestination(if (demo) "Settings · Demo" else "Settings", snackbar) {
                        SettingsScreen(m, permissionAvailable, requestPermission, versionName, isDebugBuild, biometricAvailable, authenticateBiometric,
                            ledger = ledger, demo = demo, onDemo = { demo = it }, transactions = transactions, allowDataTransfer = !demo,
                            onDataMessage = { snackbar.showSnackbar(it) }) } }
                composable("budgets") { val m = viewModel { BudgetsModel(preferences, ledger) }; Events(m, handle)
                    AppDestination("Category budgets", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { BudgetsScreen(m) } }
                composable("allow-list") { val m = viewModel { AllowListModel(apps) }; Events(m, handle)
                    AppDestination("Allowed apps", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { AllowListScreen(m) } }
                composable("choose-apps") { val m = viewModel { AllowListModel(apps) }; Events(m, handle)
                    val finish = viewModel { OnboardingModel(preferences) }; Events(finish, handle)
                    AppDestination("Choose apps", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { AllowListScreen(m, onFinish = finish::finish) } }
                composable("finance-apps") { val m = viewModel { AllowListModel(apps, finance = true) }; Events(m, handle)
                    AppDestination("Finance apps", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { AllowListScreen(m) } }
                composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    val id = it.arguments?.read { getLong("id") } ?: 0L
                    val m = viewModel { EditorModel(transactions, id, captures, ledger = ledger, apps = apps) }; Events(m, handle)
                    AppDestination(if (id == 0L) "Add transaction" else "Edit transaction", snackbar,
                        onBack = { if (!nav.popBackStack()) navigate("home") }) { EditorScreen(m, appLabels) }
                }
                composable("accounts") { val m = viewModel { LedgerSettingsModel(ledger, apps) }; Events(m, handle)
                    AppDestination("Accounts", snackbar, onBack = { nav.popBackStack() }) { AccountsScreen(m) } }
                composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    val m = viewModel { LedgerSettingsModel(ledger, apps) }; Events(m, handle)
                    AppDestination("Account details", snackbar, onBack = { nav.popBackStack() }) { AccountEditorScreen(m, it.arguments?.read { getLong("id") } ?: 0L) } }
                composable("categories") { val m = viewModel { LedgerSettingsModel(ledger, apps) }; Events(m, handle)
                    AppDestination("Categories", snackbar, onBack = { nav.popBackStack() }) { CategoriesScreen(m) } }
                composable("category/{id}/{type}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    val m = viewModel { LedgerSettingsModel(ledger, apps) }; Events(m, handle)
                    AppDestination("Category details", snackbar, onBack = { nav.popBackStack() }) {
                        CategoryEditorScreen(m, it.arguments?.read { getLong("id") } ?: 0L,
                            ph.notifly.domain.model.TransactionType.valueOf(it.arguments?.read { getString("type") } ?: "EXPENSE")) } }
                composable("account-review") { val m = viewModel { LedgerSettingsModel(ledger, apps) }; Events(m, handle)
                    AppDestination("Assign accounts", snackbar, onBack = { nav.popBackStack() }) { AccountReviewScreen(m) } }
                composable("draft/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    val m = viewModel { EditorModel(transactions, 0L, captures, ledger = ledger, apps = apps, draftId = it.arguments?.read { getLong("id") }) }; Events(m, handle)
                    AppDestination("Review transaction", snackbar, onBack = { nav.popBackStack() }) { EditorScreen(m, appLabels) } }
                composable("themes") { val m = viewModel { SettingsModel(preferences, database.transactionDao().observePendingCount()) }; Events(m, handle)
                    AppDestination("Theme palettes", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { ThemeGallery(m) } }
                composable("log") { val m = viewModel { LogModel(captures, preferences) }; Events(m, handle)
                    AppDestination("Notification log", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { LogScreen(m, appLabels) } }
                composable("from-log/{captureId}", arguments = listOf(navArgument("captureId") { type = NavType.LongType })) {
                    val m = viewModel { EditorModel(transactions, 0L, captures, it.arguments?.read { getLong("captureId") }, ledger, apps) }; Events(m, handle)
                    AppDestination("Add transaction", snackbar, onBack = { if (!nav.popBackStack()) navigate("home") }) { EditorScreen(m, appLabels) }
                }
            }
        }
        val locked = pinSet == true && !unlocked
        Box(Modifier.fillMaxSize()) {
            NavigationSuiteScaffold(
                modifier = if (locked) Modifier.clearAndSetSemantics {} else Modifier,
                navigationSuiteItems = {
                    listOf(
                        Triple("home", "Home", Res.drawable.symbol_home),
                        Triple("transactions", "Transactions", Res.drawable.symbol_receipt_long),
                        Triple("insights", "Insights", Res.drawable.symbol_pie_chart),
                        Triple("settings", "Settings", Res.drawable.symbol_settings),
                    ).forEach { (target, label, icon) ->
                        item(
                            selected = route == target,
                            onClick = { navigate(target) },
                            icon = { Icon(painterResource(icon), null) },
                            label = { Text(label) },
                        )
                    }
                },
                layoutType = navigationSuiteType,
            ) {
                destinations(Modifier.fillMaxSize())
            }
            // Overlay rather than replace, so the NavHost and its back stack survive a lock.
            if (locked) LockScreen({ preferences.verifyPin(it) }, { preferences.lockoutSeconds() }, onUnlock = { unlocked = true },
                biometric = biometricAvailable && biometricUnlock, authenticateBiometric = authenticateBiometric)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDestination(
    title: String,
    snackbar: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
            if (onBack != null) IconTooltip("Back") { IconButton(onClick = onBack) {
                Icon(painterResource(Res.drawable.symbol_arrow_back), "Back", modifier = mirroredIconModifier())
            } }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

@Composable
private fun Events(model: ScreenModel, handle: (UiEvent) -> Unit) {
    val current by rememberUpdatedState(handle)
    LaunchedEffect(model) { model.events.collect { current(it) } }
}
