package ph.notifly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
import org.koin.mp.KoinPlatformTools

sealed interface UiEvent {
    data class Navigate(val route: String) : UiEvent
    /** [undo] restores a deleted or confirmed transaction; [onUndo] reverses anything else, such as a bill payment. */
    data class Message(val text: String, val undo: Transaction? = null, val onUndo: (suspend () -> Unit)? = null) : UiEvent
}

open class ScreenModel : ViewModel() {
    protected val mutableEvents = MutableSharedFlow<UiEvent>()
    val events = mutableEvents.asSharedFlow()
    private val reporter by lazy { KoinPlatformTools.defaultContext().getOrNull()?.get<ErrorReporter>() ?: ErrorReporter.None }
    /** Runs UI work in this model's scope, preserving cancellation and reporting other failures to the user. */
    protected fun work(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: IllegalArgumentException) { mutableEvents.emit(UiEvent.Message(e.message ?: "Check the entered values.")) }
        catch (e: Exception) {
            reporter.report(e, ErrorSite.SCREEN_MODEL)
            mutableEvents.emit(UiEvent.Message("Couldn't save or load data. Please try again."))
        }
    }
    fun navigate(route: String) = work { mutableEvents.emit(UiEvent.Navigate(route)) }
}

data class LedgerState(val rows: List<Transaction> = emptyList(), val net: Long = 0L, val accounts: List<AccountBalance> = emptyList(), val drafts: Int = 0,
    /** True until the first real emission so the placeholder frame never shows figures. */
    val hideAmounts: Boolean = true,
    /** False until the saved hide preference arrives; the toggle stays disabled meanwhile. */
    val loaded: Boolean = false,
    val nextBill: NextBill? = null,
    val detectedBills: Int = 0,
)
/** [days] is negative once the bill is overdue. */
data class NextBill(val name: String, val amountMinor: Long, val days: Int)
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
    private val ledger: LedgerRepository,
    bills: BillRepository? = null,
) : ScreenModel() {
    private val billLine = (bills?.observeBills() ?: flowOf(emptyList())).combine(localToday()) { all, today ->
        val next = upcoming(all, today).all.firstOrNull()?.let { NextBill(it.bill.name, it.bill.amountMinor, (it.dueOn.toEpochDays() - today.toEpochDays()).toInt()) }
        next to all.count { it.status == TransactionStatus.NEEDS_REVIEW }
    }
    private val pendingOrder = MutableStateFlow<List<Long>?>(null)
    val savingOrder = pendingOrder.map { it != null }
    private val savedState = combine(repository.observeAll(), ledger.observeAccounts(), ledger.observeDrafts(),
        preferences.hideAmounts, preferences.homeAccountOrder) { rows, accounts, drafts, hide, order ->
        val balances = homeAccountOrder(accountBalances(accounts, rows), order)
        LedgerState(rows, balances.sumOf { it.netValue }, balances, drafts.size, hide, loaded = true)
    }
    val state = combine(savedState, pendingOrder) { saved, pending ->
        if (pending == null) saved else saved.copy(accounts = homeAccountOrder(saved.accounts, pending))
    }.combine(billLine) { s, (next, detected) -> s.copy(nextBill = next, detectedBills = detected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerState())

    fun reorderAccounts(ids: List<Long>) {
        if (pendingOrder.value != null || ids == state.value.accounts.map { it.account.id }) return
        pendingOrder.value = ids
        work {
            try {
                preferences.setHomeAccountOrder(ids)
                // Wait for the saved flow before removing the optimistic order.
                savedState.first { it.accounts == homeAccountOrder(it.accounts, ids) }
            } finally {
                pendingOrder.value = null
            }
        }
    }
    fun hideAmounts(value: Boolean) = work { preferences.setHideAmounts(value) }
    fun setBalance(id: Long, minor: Long?) = work {
        val account = ledger.observeAccounts().first().first { it.id == id }
        ledger.saveAccount(account.copy(balanceMinor = minor ?: 0L,
            balanceAsOf = if (minor == null) kotlin.time.Instant.fromEpochMilliseconds(Long.MIN_VALUE) else Clock.System.now()))
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

class InsightsModel(repository: TransactionRepository, private val preferences: AppPreferences, ledger: LedgerRepository) : ScreenModel() {
    private val days = MutableStateFlow(INSIGHT_WINDOWS.first())
    val state = combine(repository.observeAll(), days, preferences.monthlyBudget, ledger.observeCategories().map { categories -> categories.filter { it.type == TransactionType.EXPENSE && it.budgetMinor != null }.associate { it.name to it.budgetMinor!! } }, localToday()) { rows, d, budget, categoryBudgets, today ->
        val zone = TimeZone.currentSystemDefault()
        InsightsState(d, INSIGHT_WINDOWS.map { windowInsights(rows, today, it, zone) }, monthInsights(rows, today, zone),
            budget, rows.count { it.status == TransactionStatus.NEEDS_REVIEW }, categoryBudgets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsState())
    fun days(value: Int) { days.value = value }
}
/** The monthly cap and expense categories; null until both have loaded. */
data class BudgetsState(val monthly: Long?, val categories: List<Category>)
class BudgetsModel(private val preferences: AppPreferences, private val ledger: LedgerRepository) : ScreenModel() {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    val state = combine(preferences.monthlyBudget, ledger.observeCategories()) { monthly, categories ->
        BudgetsState(monthly, categories.filter { it.type == TransactionType.EXPENSE })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Writes the cap and every category whose budget changed; the caller has already checked the cap. */
    fun save(monthly: Long?, budgets: Map<Long, Long?>): kotlinx.coroutines.Job {
        if (mutableBusy.value) return work {}
        mutableBusy.value = true
        return work {
        try {
        val categories = ledger.observeCategories().first().filter { it.type == TransactionType.EXPENSE }.associateBy { it.id }
        require(budgets.keys.all { it in categories }) { "A category no longer exists. Reload budgets and try again." }
        // Navigation must not cancel halfway through writes to these separate stores.
        withContext(NonCancellable) {
            preferences.setMonthlyBudget(monthly)
            budgets.forEach { (id, minor) ->
                categories.getValue(id).takeIf { it.budgetMinor != minor }?.let { ledger.saveCategory(it.copy(budgetMinor = minor)) }
            }
        }
        mutableEvents.emit(UiEvent.Message("Budgets saved"))
        } finally { mutableBusy.value = false }
        }
    }
}
enum class TransactionFilter { ALL, NEEDS_REVIEW, INCOME, EXPENSE, TRANSFER }
data class TransactionsState(val rows: List<Transaction> = emptyList(), val filter: TransactionFilter = TransactionFilter.ALL,
    val accountNames: Map<Long, String> = emptyMap(), val query: String = "", val accountIcons: Map<Long, String> = emptyMap())
class TransactionsModel(private val repository: TransactionRepository, ledger: LedgerRepository) : ScreenModel() {
    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val query = MutableStateFlow("")
    private val appLabels = MutableStateFlow(emptyMap<String, String>())
    val state = combine(repository.observeAll(), filter, ledger.observeAccounts(), query, appLabels) { rows, f, accounts, q, labels ->
        val names = accounts.associate { it.id to it.name }
        val needle = q.trim()
        // Accepts what a row shows, e.g. "−₱1,529.00"; without cents it matches the whole peso, so "1529" finds ₱1,529.50.
        val amountNeedle = needle.filterNot { it in "₱,+-−" || it.isWhitespace() }
        val amount = parseAmountMinor(amountNeedle) ?: 0L.takeIf { amountNeedle.isNotEmpty() && amountNeedle.all { it == '0' } }
        val exactCents = '.' in amountNeedle
        TransactionsState(rows.filter { when (f) {
            TransactionFilter.ALL -> true
            TransactionFilter.NEEDS_REVIEW -> it.status == TransactionStatus.NEEDS_REVIEW
            TransactionFilter.INCOME -> it.type == TransactionType.INCOME
            TransactionFilter.EXPENSE -> it.type == TransactionType.EXPENSE
            TransactionFilter.TRANSFER -> it.type == TransactionType.TRANSFER
        } && (needle.isEmpty() || amount != null && (if (exactCents) it.amountMinor == amount else it.amountMinor / 100 == amount / 100) || listOfNotNull(it.title, it.category, names[it.accountId], it.toAccountId?.let(names::get),
            it.sourceApp, it.sourceApp?.let(labels::get)).any { field -> field.contains(needle, ignoreCase = true) }) },
            f, names, q, accountIcons(accounts))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsState())
    fun search(value: String) { query.value = value }
    /** Lets search match a source app by the name the user sees. */
    fun appLabels(value: Map<String, String>) { appLabels.value = value }
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
    val accountId: Long? = null, val toAccountId: Long? = null, val categoryId: Long? = null,
    val accounts: List<Account> = emptyList(), val categories: List<Category> = emptyList(), val apps: List<AllowedApp> = emptyList(),
    val fromDraft: Boolean = false, val accountError: String? = null, val toAccountError: String? = null,
    val fee: String = "", val feeError: String? = null,
    /** Field values as loaded; null until loading finishes. */
    private val baseline: List<Any?>? = null,
) {
    private fun fields() = listOf(title, amount, category, type, date, time, accountId, toAccountId, sourceApp, fee)
    val loaded get() = baseline != null
    val dirty get() = loaded && baseline != fields()
    internal fun withBaseline() = copy(baseline = fields())
    /**
     * Bank-to-bank transfers out of an account without free transfers can carry a fee. A transfer
     * that already has one keeps the field, so later account changes never silently drop it.
     */
    val feeApplies get() = type == TransactionType.TRANSFER && ((original?.feeMinor ?: 0) > 0 ||
        accounts.find { it.id == accountId }?.let { it.type == AccountType.BANK && !it.freeTransfer } == true &&
        accounts.find { it.id == toAccountId }?.type == AccountType.BANK)
}
class EditorModel(private val repository: TransactionRepository, id: Long,
                  captures: CaptureRepository? = null, captureId: Long? = null,
                  private val ledger: LedgerRepository, apps: AllowListRepository, private val draftId: Long? = null,
                  private val bills: BillRepository? = null, private val billId: Long? = null) : ScreenModel() {
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    /** Set when this editor records a bill payment: the occurrence being paid and the bill's name. */
    private var billDue: LocalDate? = null
    private var billName: String = ""
    init { work {
        ledger.initialize()
        val t = if (id == 0L) null else repository.byId(id)
        mutableState.value = if (id != 0L && t == null) EditorState(error = "Transaction no longer exists.")
        else {
            val local = (t?.occurredAt ?: Clock.System.now()).toLocalDateTime(TimeZone.currentSystemDefault())
            EditorState(t, t?.title.orEmpty(), t?.let { amountText(it.amountMinor) }.orEmpty(),
                t?.category ?: "Other", t?.type ?: TransactionType.EXPENSE,
                accountId = t?.accountId, toAccountId = t?.toAccountId, categoryId = t?.categoryId, sourceApp = t?.sourceApp,
                fee = t?.feeMinor?.takeIf { it > 0 }?.let(::amountText).orEmpty(),
                date = local.date.toString(),
                time = if (t == null) "00:00" else local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0'))
        }
        val draft = draftId?.let { value -> ledger.observeDrafts().first().find { it.id == value } }
        if (draftId != null && draft == null) { mutableState.value = state.value.copy(ready = false, error = "Draft no longer exists."); return@work }
        if (draft != null) {
            val local = draft.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault())
            mutableState.value = state.value.copy(fromDraft = true, title = draft.title, amount = amountText(draft.amountMinor),
                type = draft.type, category = if (draft.type == TransactionType.TRANSFER) "Transfer" else "Other",
                accountId = if (draft.type == TransactionType.TRANSFER && draft.inbound == true) null else draft.accountId,
                toAccountId = if (draft.type == TransactionType.TRANSFER && draft.inbound == true) draft.accountId else draft.toAccountId,
                sourceApp = draft.sourceApp, captureId = draft.captureId, date = local.date.toString(),
                time = local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0'))
        }
        if (billId != null) {
            val bill = bills?.byId(billId)
            val due = bill?.nextDue
            if (bill == null || due == null) { mutableState.value = state.value.copy(ready = false, error = "Bill no longer exists."); return@work }
            billDue = due
            billName = bill.name
            val categoryId = ledger.observeCategories().first().find { it.type == TransactionType.EXPENSE && it.name == bill.category }?.id
            mutableState.value = state.value.copy(type = TransactionType.EXPENSE, title = bill.name, amount = amountText(bill.amountMinor),
                category = bill.category, categoryId = categoryId, accountId = bill.accountId)
        }
        val linkedCaptureId = captureId ?: draft?.captureId ?: t?.captureId
        if (linkedCaptureId != null) {
            val capture = captures?.observeLog()?.first()?.find { it.id == linkedCaptureId }
            mutableState.value = state.value.copy(sourceText = capture?.body, sourceApp = state.value.sourceApp ?: apps.observeAll().first().find { it.packageName == capture?.sourceApp || it.label == capture?.sourceApp }?.packageName, captureId = capture?.id ?: state.value.captureId)
        }
        mutableState.value = state.value.withBaseline()
        val found = id == 0L || t != null
        viewModelScope.launch {
            combine(ledger.observeAccounts(), ledger.observeCategories(), apps.observeAll()) { accounts, categories, allowed ->
                Triple(accounts, categories, allowed)
            }.collect { (accounts, categories, allowed) ->
                // Saving waits for accounts, since validation and the transfer fee depend on them.
                mutableState.value = state.value.copy(accounts = accounts, categories = categories, apps = allowed, ready = found)
            }
        }
    } }
    fun edit(title: String = state.value.title, amount: String = state.value.amount,
             category: String = state.value.category, type: TransactionType = state.value.type,
             date: String = state.value.date, time: String = state.value.time,
             accountId: Long? = state.value.accountId, toAccountId: Long? = state.value.toAccountId,
             sourceApp: String? = state.value.sourceApp, fee: String = state.value.fee) {
        // Loading overwrites fields, and the baseline taken after it would hide earlier edits.
        if (!state.value.loaded) return
        val chosen = state.value.categories.find { it.name == category && it.type == type }
        val suggestion = if (sourceApp != state.value.sourceApp && accountId == null)
            state.value.accounts.filter { !it.archived && sourceApp in it.linkedApps }.singleOrNull()?.id else accountId
        mutableState.value = state.value.copy(title = title, amount = amount,
            category = if (type == TransactionType.TRANSFER) "Transfer" else if (type != state.value.type && chosen == null) "Other" else category,
            categoryId = if (type == TransactionType.TRANSFER) null else chosen?.id, type = type, date = date, time = time,
            accountId = suggestion, toAccountId = toAccountId.takeIf { type == TransactionType.TRANSFER }, sourceApp = sourceApp,
            fee = fee, feeError = null, accountError = null, toAccountError = null, error = null, titleError = null, amountError = null, dateError = null, timeError = null)
    }
    /**
     * Validates the editor fields and asynchronously saves a confirmed transaction, then navigates
     * to Transactions, or to Bills after linking a bill payment. Bill payments offer Undo that removes
     * the settlement and saved transaction. A rejected settlement removes a newly created transaction
     * and shows an error. Other save failures attempt to remove a newly inserted manual transaction.
     * Calls before loading or during a save are ignored. Invalid fields and save failures are exposed
     * in [state]; coroutine cancellation is rethrown.
     */
    fun save() {
        val s = state.value
        if (!s.ready || s.saving) return
        val amount = parseAmountMinor(s.amount)
        val date = runCatching { LocalDate.parse(s.date) }.getOrNull()
        val time = runCatching { LocalTime.parse(s.time) }.getOrNull()
        val fee = if (!s.feeApplies || s.fee.isBlank()) 0L else parseAmountMinor(s.fee)
        if (date == null || time == null || s.title.isBlank() || amount == null || fee == null) {
            mutableState.value = s.copy(
                error = "Correct the highlighted fields.",
                titleError = if (s.title.isBlank()) "Add a description." else null,
                amountError = if (amount == null) "Enter an amount above zero, with at most two decimal places." else null,
                dateError = if (date == null) "Enter a valid date as YYYY-MM-DD." else null,
                timeError = if (time == null) "Enter a valid time as HH:MM." else null,
                feeError = if (fee == null) "Enter a fee above zero with at most two decimal places, or leave it blank." else null,
            )
            return
        }
        val account = s.accounts.find { it.id == s.accountId && (!it.archived || it.id == s.original?.accountId) }
        val destination = s.accounts.find { it.id == s.toAccountId && (!it.archived || it.id == s.original?.toAccountId) }
        if (account == null || (s.type == TransactionType.TRANSFER && (destination == null || destination.id == account.id))) {
            mutableState.value = s.copy(accountError = if (account == null) "Choose an account." else null,
                toAccountError = if (s.type == TransactionType.TRANSFER) "Choose a different destination account." else null)
            return
        }
        mutableState.value = s.copy(saving = true)
        val occurredAt = date.atTime(time).toInstant(TimeZone.currentSystemDefault())
        work {
            var createdId: Long? = null
            try {
                val transaction = s.original?.copy(title = s.title.trim(), amountMinor = amount,
                    category = s.category, categoryId = s.categoryId, type = s.type, status = TransactionStatus.CONFIRMED,
                    occurredAt = occurredAt, accountId = account.id, toAccountId = s.toAccountId,
                    sourceApp = s.sourceApp, feeMinor = fee)
                    ?: Transaction(title = s.title.trim(), amountMinor = amount, type = s.type,
                        status = TransactionStatus.CONFIRMED, category = s.category, categoryId = s.categoryId,
                        occurredAt = occurredAt, createdAt = Clock.System.now(), sourceApp = s.sourceApp,
                        captureId = s.captureId, accountId = account.id, toAccountId = s.toAccountId, feeMinor = fee)
                val savedId = if (draftId != null) ledger.confirmDraft(draftId, transaction) else repository.upsert(transaction)
                if (s.original == null && draftId == null) createdId = savedId
                val due = billDue
                val paymentId = if (billId != null && due != null && bills != null) bills.settle(billId, due, savedId) else -1L
                if (billId != null && paymentId < 0) {
                    if (s.original == null) repository.delete(savedId)
                    mutableState.value = state.value.copy(error = "This bill occurrence changed. No payment was recorded; please try again.")
                    return@work
                }
                if (paymentId > 0) {
                    mutableEvents.emit(UiEvent.Navigate("bills"))
                    mutableEvents.emit(UiEvent.Message("$billName marked paid", onUndo = { bills?.unsettle(paymentId); repository.delete(savedId) }))
                } else mutableEvents.emit(UiEvent.Navigate("transactions"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                createdId?.let { runCatching { repository.delete(it) } }
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

data class SettingsState(val palette: NotiflyPalette = NotiflyPalette.Ube, val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val offline: Boolean = true, val pending: Int = 0, val crashReporting: Boolean = false,
    val pinSet: Boolean = false, val biometric: Boolean = false, val billReminderDays: Int? = null)
class SettingsModel(private val preferences: AppPreferences, pending: Flow<Int>) : ScreenModel() {
    val state = combine(preferences.palette, preferences.themeMode, preferences.offline, pending, preferences.crashReporting, ::SettingsState)
        .combine(preferences.pinSet) { s, pin -> s.copy(pinSet = pin) }
        .combine(preferences.biometricUnlock) { s, bio -> s.copy(biometric = bio) }
        .combine(preferences.billReminderDays) { s, days -> s.copy(billReminderDays = days) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())
    /** Asynchronously stores the reminder lead time in days, or disables reminders for null; non-cancellation failures become UI messages. */
    fun billReminders(days: Int?) = work { preferences.setBillReminderDays(days) }
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
        navigate("onboarding/0")
    }
}
private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

data class BillsState(
    val today: LocalDate = today(),
    val bills: List<Bill> = emptyList(),
    val payments: List<BillPayment> = emptyList(),
    /** Every status: a NEEDS_REVIEW expense is still a candidate payment, and linking it confirms it. */
    val transactions: List<Transaction> = emptyList(),
    val accountNames: Map<Long, String> = emptyMap(),
    /** True until the saved preference arrives, so the placeholder frame never shows figures. */
    val hideAmounts: Boolean = true,
    val reminderDays: Int? = null,
    /** True until the saved preference arrives, so the prompt never flashes. */
    val promptDismissed: Boolean = true,
    val loaded: Boolean = false,
) {
    val due = upcoming(bills, today)
    val detected = bills.filter { it.status == TransactionStatus.NEEDS_REVIEW }
    val summary = due.summary(today)
}

class BillsModel(private val bills: BillRepository, private val transactions: TransactionRepository,
                 ledger: LedgerRepository, private val preferences: AppPreferences) : ScreenModel() {
    val state = combine(bills.observeBills(), bills.observePayments(), transactions.observeAll(), ledger.observeAccounts()) { b, p, t, a ->
        BillsState(bills = b, payments = p, transactions = t, accountNames = a.associate { it.id to it.name }, loaded = true)
    }.combine(preferences.hideAmounts) { s, hide -> s.copy(hideAmounts = hide) }
        .combine(preferences.billReminderDays) { s, days -> s.copy(reminderDays = days) }
        .combine(preferences.billPromptDismissed) { s, dismissed -> s.copy(promptDismissed = dismissed) }
        .combine(localToday()) { s, day -> s.copy(today = day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillsState())

    /** Asynchronously confirms or merges the bill and emits a success message; non-cancellation failures become UI messages. */
    fun confirm(bill: Bill) = work {
        bills.confirm(bill.id)
        mutableEvents.emit(UiEvent.Message("${bill.name} bill confirmed"))
    }
    /** Deletes the bill and emits Undo to restore the supplied bill, without its payment history; non-cancellation failures become UI messages. */
    fun dismiss(bill: Bill) = work {
        bills.delete(bill.id)
        mutableEvents.emit(UiEvent.Message("${bill.name} bill dismissed", onUndo = { bills.save(bill) }))
    }
    /** The user's tap is the confirmation, so a NEEDS_REVIEW expense is confirmed as it is linked. */
    fun link(bill: Bill, due: LocalDate, transaction: Transaction) = work {
        val review = transaction.status == TransactionStatus.NEEDS_REVIEW
        if (review) transactions.upsert(transaction.copy(status = TransactionStatus.CONFIRMED))
        settle(bill, due, transaction.id, "${bill.name} marked paid") { if (review) transactions.upsert(transaction) }
    }
    /** Asynchronously records the occurrence as skipped and offers Undo; non-cancellation failures become UI messages. */
    fun skip(bill: Bill, due: LocalDate) = work { settle(bill, due, null, "${bill.name} skipped") {} }
    /**
     * Records a payment or skip and emits [text] with Undo that reverses it before calling [undoExtra].
     * A rejected occurrence emits nothing. Repository failures propagate to the caller.
     */
    private suspend fun settle(bill: Bill, due: LocalDate, transactionId: Long?, text: String, undoExtra: suspend () -> Unit) {
        val paymentId = bills.settle(bill.id, due, transactionId)
        if (paymentId < 0) return
        mutableEvents.emit(UiEvent.Message(text, onUndo = { bills.unsettle(paymentId); undoExtra() }))
    }
    /** One day before is the default; the lead time can be changed in Settings. */
    fun turnOnReminders() = work { preferences.setBillReminderDays(1) }
    /** Persists dismissal of the reminder prompt asynchronously; non-cancellation failures become UI messages. */
    fun dismissPrompt() = work { preferences.setBillPromptDismissed(true) }
}

data class BillEditorState(
    val original: Bill? = null, val name: String = "", val amount: String = "",
    val due: LocalDate = today(), val repeat: BillRepeat = BillRepeat.MONTHLY,
    val category: String = "Bills", val accountId: Long? = null,
    val nameError: String? = null, val amountError: String? = null, val error: String? = null,
    val ready: Boolean = false, val saving: Boolean = false,
    val categories: List<String> = emptyList(), val accounts: List<Account> = emptyList(),
) {
    val reviewing get() = original?.status == TransactionStatus.NEEDS_REVIEW
}

class BillEditorModel(private val bills: BillRepository, ledger: LedgerRepository, id: Long, prefillDue: LocalDate? = null) : ScreenModel() {
    private val mutableState = MutableStateFlow(BillEditorState())
    val state = mutableState.asStateFlow()
    init { work {
        ledger.initialize()
        val bill = if (id == 0L) null else bills.byId(id)
        if (id != 0L && bill == null) { mutableState.value = BillEditorState(error = "Bill no longer exists."); return@work }
        mutableState.value = BillEditorState(bill, bill?.name.orEmpty(), bill?.let { amountText(it.amountMinor) }.orEmpty(),
            due = bill?.let { it.nextDue ?: it.startsOn } ?: prefillDue ?: today(), repeat = bill?.repeat ?: BillRepeat.MONTHLY,
            category = bill?.category ?: "Bills", accountId = bill?.accountId, ready = true)
        viewModelScope.launch {
            combine(ledger.observeCategories(), ledger.observeAccounts()) { categories, accounts ->
                categories.filter { it.type == TransactionType.EXPENSE && !it.archived }.map { it.name } to accounts
            }.collect { (categories, accounts) ->
                mutableState.value = state.value.copy(categories = categories, accounts = accounts.filter { !it.archived || it.id == state.value.accountId })
            }
        }
    } }
    /** Updates bill editor fields and clears inline errors without saving. */
    fun edit(name: String = state.value.name, amount: String = state.value.amount, due: LocalDate = state.value.due,
             repeat: BillRepeat = state.value.repeat, category: String = state.value.category, accountId: Long? = state.value.accountId) {
        mutableState.value = state.value.copy(name = name, amount = amount, due = due, repeat = repeat, category = category,
            accountId = accountId, nameError = null, amountError = null, error = null)
    }
    /**
     * Builds a bill from the editor, setting inline errors and returning null for a blank name or an
     * invalid amount (including nonpositive, overflowing, or more than two decimal places).
     * Changing the due date or repeat schedule resets the anchor, settled count, and reminder marker.
     */
    private fun build(): Bill? {
        val s = state.value
        val amount = parseAmountMinor(s.amount)
        if (s.name.isBlank() || amount == null) {
            mutableState.value = s.copy(nameError = if (s.name.isBlank()) "Add a bill name." else null,
                amountError = if (amount == null) "Enter an amount above zero, with at most two decimal places." else null)
            return null
        }
        val original = s.original ?: return Bill(name = s.name.trim(), amountMinor = amount, category = s.category, accountId = s.accountId,
            startsOn = s.due, repeat = s.repeat, status = TransactionStatus.CONFIRMED, createdAt = Clock.System.now())
        // Moving the due date or the schedule re-anchors the series at the chosen date.
        val moved = s.due != (original.nextDue ?: original.startsOn) || s.repeat != original.repeat
        return original.copy(name = s.name.trim(), amountMinor = amount, category = s.category, accountId = s.accountId, repeat = s.repeat,
            startsOn = if (moved) s.due else original.startsOn,
            remindedFor = if (moved) null else original.remindedFor)
    }
    /**
     * Validates fields, then runs [action] asynchronously if loaded and idle. On success, navigates to
     * Bills and shows the returned message. Non-cancellation failures become UI messages; the saving
     * flag is cleared when the action finishes or throws.
     */
    private fun finish(action: suspend (Bill) -> String) {
        val bill = build() ?: return
        if (!state.value.ready || state.value.saving) return
        mutableState.value = state.value.copy(saving = true)
        work {
            try {
                val text = action(bill)
                mutableEvents.emit(UiEvent.Navigate("bills"))
                mutableEvents.emit(UiEvent.Message(text))
            } finally { mutableState.value = state.value.copy(saving = false) }
        }
    }
    /** Validates and saves the bill asynchronously, then returns to Bills; non-cancellation failures become UI messages. */
    fun save() = finish { bills.save(it); "${it.name} saved" }
    /** Saves the edits, then confirms; confirming may update an existing bill from the same app instead. */
    fun confirm() = finish { bills.confirm(bills.save(it)); "${it.name} bill confirmed" }
    fun dismiss() = work {
        val bill = state.value.original ?: return@work
        bills.delete(bill.id)
        mutableEvents.emit(UiEvent.Navigate("bills"))
        mutableEvents.emit(UiEvent.Message("${bill.name} bill dismissed", onUndo = { bills.save(bill) }))
    }
    fun delete() = work {
        val bill = state.value.original ?: return@work
        bills.delete(bill.id)
        mutableEvents.emit(UiEvent.Navigate("bills"))
        mutableEvents.emit(UiEvent.Message("${bill.name} deleted"))
    }
}

/** [checked] counts every enabled app, not just those matching [query]. */
data class AllowListState(val apps: List<AllowedApp> = emptyList(), val query: String = "", val finance: Boolean = false, val checked: Int = 0)
/** In [finance] mode, lists only allowed apps and toggles whether each one takes part in transfer detection. */
class AllowListModel(private val repository: AllowListRepository, private val finance: Boolean = false) : ScreenModel() {
    private val query = MutableStateFlow("")
    /** Packages listening as of the first load. Keeps the pinned-at-top section from reordering under the user's finger; refreshed only when the screen (and model) is recreated. */
    private var pinned: Set<String>? = null
    val state = combine(repository.observeAll(), query.debounce(150).onStart { emit(query.value) }) { all, q ->
        val apps = if (finance) all.filter { it.listening } else all
        val p = pinned ?: apps.filter { it.isChecked() }.map { it.packageName }.toSet().also { pinned = it }
        val visible = apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        AllowListState(visible.sortedBy { it.packageName !in p }, q, finance, apps.count { it.isChecked() })
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
/** First run has no account step: finishing starts offline, and Settings keeps sign-in. */
class OnboardingModel(private val preferences: AppPreferences) : ScreenModel() {
    fun finish() = work {
        preferences.setOffline(true)
        preferences.completeOnboarding()
        mutableEvents.emit(UiEvent.Navigate("home"))
    }
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
