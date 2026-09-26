package ph.notifly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ph.notifly.data.local.AppPreferences
import ph.notifly.data.local.PinResult
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.diagnostics.ErrorSite
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import ph.notifly.ui.theme.NotiflyPalette
import ph.notifly.ui.theme.ThemeMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext

sealed interface UiEvent {
    data class Navigate(val route: String) : UiEvent
    data class Message(val text: String, val undo: Transaction? = null) : UiEvent
}

open class ScreenModel : ViewModel() {
    protected val mutableEvents = MutableSharedFlow<UiEvent>()
    val events = mutableEvents.asSharedFlow()
    private val reporter by lazy { GlobalContext.getOrNull()?.get<ErrorReporter>() ?: ErrorReporter.None }
    /** Runs UI work in this model's scope, preserving cancellation and reporting other failures to the user. */
    protected fun work(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            reporter.report(e, ErrorSite.SCREEN_MODEL)
            mutableEvents.emit(UiEvent.Message("Couldn't save or load data. Please try again."))
        }
    }
    fun navigate(route: String) = work { mutableEvents.emit(UiEvent.Navigate(route)) }
}

data class LedgerState(val rows: List<Transaction> = emptyList(), val net: Long = 0L, val accounts: List<AccountBalance> = emptyList())
data class InsightsState(
    val days: Int = INSIGHT_WINDOWS.first(),
    val windows: List<WindowInsights> = emptyList(),
    val month: MonthInsights? = null,
    val budget: Long? = null,
    val pending: Int = 0,
    val categoryBudgets: Map<String, Long> = emptyMap(),
) {
    val selected get() = windows.firstOrNull { it.days == days }
}
class HomeModel(
    private val repository: TransactionRepository,
    apps: AllowListRepository,
    private val preferences: AppPreferences,
) : ScreenModel() {
    val state = combine(repository.observeAll(), repository.observeConfirmedNetMinor(), apps.observeAll(), preferences.accountBalances) {
        rows, net, allowed, manual -> LedgerState(rows, net, accountBalances(allowed, rows, manual))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerState())
    /** Saves a balance in minor units with the current time as its cutoff, or clears it when null. */
    fun setBalance(packageName: String, minor: Long?) = work {
        preferences.setAccountBalance(packageName, minor?.let { ph.notifly.data.local.ManualBalance(it, Clock.System.now()) })
    }
    fun confirm(t: Transaction) = work {
        repository.upsert(t.copy(status = TransactionStatus.CONFIRMED))
        mutableEvents.emit(UiEvent.Message("Transaction confirmed", undo = t))
    }
}
/** Today's local date, re-emitted when the day changes. */
private fun localToday() = flow {
    while (true) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(zone).date
        emit(today)
        // Capped: Android's delay clock pauses in deep sleep, so a single sleep until midnight can overshoot.
        delay(minOf(today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone) - now, 1.minutes))
    }
}.distinctUntilChanged()

class InsightsModel(repository: TransactionRepository, private val preferences: AppPreferences) : ScreenModel() {
    private val days = MutableStateFlow(INSIGHT_WINDOWS.first())
    val state = combine(repository.observeAll(), days, preferences.monthlyBudget, preferences.categoryBudgets, localToday()) { rows, d, budget, categoryBudgets, today ->
        val zone = TimeZone.currentSystemDefault()
        InsightsState(d, INSIGHT_WINDOWS.map { windowInsights(rows, today, it, zone) }, monthInsights(rows, today, zone),
            budget, rows.count { it.status == TransactionStatus.NEEDS_REVIEW }, categoryBudgets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsState())
    fun days(value: Int) { days.value = value }
    fun budget(minor: Long?) = work { preferences.setMonthlyBudget(minor) }
}
class BudgetsModel(private val preferences: AppPreferences) : ScreenModel() {
    val state = preferences.categoryBudgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    fun budget(category: String, minor: Long?) = work { preferences.setCategoryBudget(category, minor) }
}
enum class TransactionFilter { ALL, NEEDS_REVIEW, INCOME, EXPENSE, TRANSFER }
data class TransactionsState(val rows: List<Transaction> = emptyList(), val filter: TransactionFilter = TransactionFilter.ALL)
class TransactionsModel(private val repository: TransactionRepository) : ScreenModel() {
    private val filter = MutableStateFlow(TransactionFilter.ALL)
    val state = combine(repository.observeAll(), filter) { rows, f ->
        TransactionsState(rows.filter { when (f) {
            TransactionFilter.ALL -> true
            TransactionFilter.NEEDS_REVIEW -> it.status == TransactionStatus.NEEDS_REVIEW
            TransactionFilter.INCOME -> it.type == TransactionType.INCOME
            TransactionFilter.EXPENSE -> it.type == TransactionType.EXPENSE
            TransactionFilter.TRANSFER -> it.type == TransactionType.TRANSFER
        } }, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsState())
    fun filter(value: TransactionFilter) { filter.value = value }
    fun confirm(t: Transaction) = work {
        repository.upsert(t.copy(status = TransactionStatus.CONFIRMED))
        mutableEvents.emit(UiEvent.Message("Transaction confirmed", undo = t))
    }
    fun delete(t: Transaction) = work {
        repository.delete(t.id)
        mutableEvents.emit(UiEvent.Message("Transaction deleted", undo = t))
    }
    /**
     * Confirms supplied review rows asynchronously, leaving other statuses unchanged.
     * Non-cancellation failures stop the batch and emit an error message; earlier saves remain committed.
     */
    fun confirmAll(rows: List<Transaction>) = work {
        val pending = rows.filter { it.status == TransactionStatus.NEEDS_REVIEW }
        pending.forEach { repository.upsert(it.copy(status = TransactionStatus.CONFIRMED)) }
        if (pending.isNotEmpty()) mutableEvents.emit(UiEvent.Message("${pending.size} transactions confirmed"))
    }
    /**
     * Deletes supplied rows asynchronously without an undo payload.
     * Non-cancellation failures stop the batch and emit an error message; earlier deletions remain committed.
     */
    fun deleteAll(rows: List<Transaction>) = work {
        rows.forEach { repository.delete(it.id) }
        if (rows.isNotEmpty()) mutableEvents.emit(UiEvent.Message("${rows.size} transactions deleted"))
    }
}

data class EditorState(
    val original: Transaction? = null, val title: String = "", val amount: String = "",
    val category: String = "Other", val type: TransactionType = TransactionType.EXPENSE,
    val error: String? = null, val ready: Boolean = false, val saving: Boolean = false,
    val titleError: String? = null, val amountError: String? = null,
    val dateError: String? = null, val timeError: String? = null,
    val sourceText: String? = null, val sourceApp: String? = null, val captureId: Long? = null,
    val date: String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
    val time: String = "00:00",
)
class EditorModel(private val repository: TransactionRepository, id: Long,
                  captures: CaptureRepository? = null, captureId: Long? = null) : ScreenModel() {
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    init { work {
        val t = if (id == 0L) null else repository.byId(id)
        mutableState.value = if (id != 0L && t == null) EditorState(error = "Transaction no longer exists.")
        else {
            val local = (t?.occurredAt ?: Clock.System.now()).toLocalDateTime(TimeZone.currentSystemDefault())
            EditorState(t, t?.title.orEmpty(), t?.let { amountText(it.amountMinor) }.orEmpty(),
                t?.category ?: "Other", t?.type ?: TransactionType.EXPENSE, ready = true,
                date = local.date.toString(),
                time = if (t == null) "00:00" else local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0'))
        }
        val linkedCaptureId = captureId ?: t?.captureId
        if (linkedCaptureId != null) {
            val capture = captures?.observeLog()?.first()?.find { it.id == linkedCaptureId }
            mutableState.value = state.value.copy(sourceText = capture?.body, sourceApp = capture?.sourceApp, captureId = capture?.id)
        }
    } }
    fun edit(title: String = state.value.title, amount: String = state.value.amount,
             category: String = state.value.category, type: TransactionType = state.value.type,
             date: String = state.value.date, time: String = state.value.time) {
        mutableState.value = state.value.copy(title = title, amount = amount, category = category, type = type, date = date, time = time,
            error = null, titleError = null, amountError = null, dateError = null, timeError = null)
    }
    /**
     * Validates the editor fields and asynchronously saves a confirmed transaction, then navigates
     * to the list. Calls before loading or during a save are ignored. Invalid fields and save failures
     * are exposed in [state]; coroutine cancellation is rethrown.
     */
    fun save() {
        val s = state.value
        if (!s.ready || s.saving) return
        val amount = parseAmountMinor(s.amount)
        val date = runCatching { LocalDate.parse(s.date) }.getOrNull()
        val time = runCatching { LocalTime.parse(s.time) }.getOrNull()
        if (date == null || time == null || s.title.isBlank() || amount == null) {
            mutableState.value = s.copy(
                error = "Correct the highlighted fields.",
                titleError = if (s.title.isBlank()) "Add a description." else null,
                amountError = if (amount == null) "Enter an amount above zero, with at most two decimal places." else null,
                dateError = if (date == null) "Enter a valid date as YYYY-MM-DD." else null,
                timeError = if (time == null) "Enter a valid time as HH:MM." else null,
            )
            return
        }
        mutableState.value = s.copy(saving = true)
        val occurredAt = date.atTime(time).toInstant(TimeZone.currentSystemDefault())
        work {
            try {
                repository.upsert(s.original?.copy(title = s.title.trim(), amountMinor = amount,
                    category = s.category, type = s.type, status = TransactionStatus.CONFIRMED, occurredAt = occurredAt)
            ?: Transaction(title = s.title.trim(), amountMinor = amount, type = s.type,
                status = TransactionStatus.CONFIRMED, category = s.category,
                occurredAt = occurredAt, createdAt = Clock.System.now(), sourceApp = s.sourceApp, captureId = s.captureId))
                mutableEvents.emit(UiEvent.Navigate("transactions"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = state.value.copy(error = "Couldn't save the transaction. Please try again.")
            } finally { mutableState.value = state.value.copy(saving = false) }
        }
    }
    fun delete() = work {
        val t = state.value.original ?: return@work
        repository.delete(t.id)
        mutableEvents.emit(UiEvent.Message("Transaction deleted", t))
    }
}

data class SettingsState(val palette: NotiflyPalette = NotiflyPalette.Evergreen, val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val offline: Boolean = true, val pending: Int = 0, val crashReporting: Boolean = false,
    val pinSet: Boolean = false, val biometric: Boolean = false)
class SettingsModel(private val preferences: AppPreferences, pending: Flow<Int>) : ScreenModel() {
    val state = combine(preferences.palette, preferences.themeMode, preferences.offline, pending, preferences.crashReporting, ::SettingsState)
        .combine(preferences.pinSet) { s, pin -> s.copy(pinSet = pin) }
        .combine(preferences.biometricUnlock) { s, bio -> s.copy(biometric = bio) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())
    fun palette(value: NotiflyPalette) = work { preferences.setPalette(value) }
    fun themeMode(value: ThemeMode) = work { preferences.setThemeMode(value) }
    fun offline(value: Boolean) = work {
        if (!value) mutableEvents.emit(UiEvent.Message("Cloud sync is not configured yet. Changes remain saved on this device."))
        else preferences.setOffline(true)
    }
    fun crashReporting(value: Boolean) = work { preferences.setCrashReporting(value) }
    fun setPin(pin: String) = work { preferences.setPin(pin); mutableEvents.emit(UiEvent.Message("App PIN set.")) }
    /** Verifies [current] before removing the PIN; wrong attempts and lockout are reported as UI messages. */
    fun clearPin(current: String) = work {
        when (val result = preferences.verifyPin(current)) {
            PinResult.Ok -> { preferences.clearPin(); mutableEvents.emit(UiEvent.Message("App PIN removed.")) }
            PinResult.Wrong -> mutableEvents.emit(UiEvent.Message("Wrong PIN."))
            is PinResult.LockedFor -> mutableEvents.emit(UiEvent.Message("Too many attempts. Try again in ${result.seconds}s."))
        }
    }
    fun biometric(value: Boolean) = work { preferences.setBiometricUnlock(value) }
    fun restartOnboarding() = work {
        preferences.resetOnboarding()
        navigate("onboarding")
    }
}
data class AllowListState(val apps: List<AllowedApp> = emptyList(), val query: String = "", val finance: Boolean = false)
/** In [finance] mode, lists only allowed apps and toggles whether each one takes part in transfer detection. */
class AllowListModel(private val repository: AllowListRepository, private val finance: Boolean = false) : ScreenModel() {
    private val query = MutableStateFlow("")
    /** Packages listening as of the first load. Keeps the pinned-at-top section from reordering under the user's finger; refreshed only when the screen (and model) is recreated. */
    private var pinned: Set<String>? = null
    val state = combine(repository.observeAll(), query.debounce(150).onStart { emit(query.value) }) { all, q ->
        val apps = if (finance) all.filter { it.listening } else all
        val p = pinned ?: apps.filter { it.isChecked() }.map { it.packageName }.toSet().also { pinned = it }
        val visible = apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        AllowListState(visible.sortedBy { it.packageName !in p }, q, finance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AllowListState(finance = finance))
    /** Returns the finance flag in finance mode, or the listening flag otherwise. */
    fun AllowedApp.isChecked() = if (this@AllowListModel.finance) finance else listening
    /** Asynchronously toggles the finance or listening flag selected by this model's mode. */
    fun toggle(app: AllowedApp) = work {
        if (finance) repository.setFinance(app.packageName, !app.finance)
        else repository.setListening(app.packageName, !app.listening)
    }
    fun search(value: String) { query.value = value }
}
data class OnboardingState(val page: Int = 0)
class OnboardingModel : ScreenModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state = mutableState.asStateFlow()
    fun next() { mutableState.value = OnboardingState((state.value.page + 1).coerceAtMost(3)) }
}
data class AuthState(val signup: Boolean = false, val email: String = "", val password: String = "",
    val error: String? = null, val emailError: String? = null, val passwordError: String? = null)
class AuthModel(private val preferences: AppPreferences, private val demo: Boolean) : ScreenModel() {
    private val mutableState = MutableStateFlow(AuthState())
    val state = mutableState.asStateFlow()
    fun edit(email: String = state.value.email, password: String = state.value.password, signup: Boolean = state.value.signup) {
        mutableState.value = AuthState(signup, email, password)
    }
    /**
     * Checks for an email containing `@` and a password of at least eight characters.
     * Valid demo submissions complete onboarding in offline mode; other valid submissions report unavailable cloud sign-in.
     */
    fun submit() = work {
        val s = state.value
        if (!s.email.contains('@') || s.password.length < 8) {
            mutableState.value = s.copy(
                emailError = if (!s.email.contains('@')) "Enter a valid email." else null,
                passwordError = if (s.password.length < 8) "Use at least 8 characters." else null,
            )
        } else if (demo) { startOffline() }
        else { mutableState.value = s.copy(error = "Cloud sign-in is not configured. You can continue offline.") }
    }
    fun startOffline() = work {
        preferences.setOffline(true)
        preferences.completeOnboarding()
        mutableEvents.emit(UiEvent.Navigate("home"))
    }
}
