package ph.notifly.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.*
import ph.notifly.ui.theme.NotiflyPalette
import kotlin.time.Clock

sealed interface UiEvent {
    data class Navigate(val route: String) : UiEvent
    data class Message(val text: String, val undo: Transaction? = null) : UiEvent
}

open class ScreenModel : ViewModel() {
    protected val mutableEvents = MutableSharedFlow<UiEvent>()
    val events = mutableEvents.asSharedFlow()
    protected fun work(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableEvents.emit(UiEvent.Message("Couldn't save or load data. Please try again.")) }
    }
    fun navigate(route: String) = work { mutableEvents.emit(UiEvent.Navigate(route)) }
}

data class LedgerState(val rows: List<Transaction> = emptyList(), val net: Long = 0L)
class HomeModel(repository: TransactionRepository) : ScreenModel() {
    val state = combine(repository.observeAll(), repository.observeConfirmedNetMinor()) { rows, net -> LedgerState(rows, net) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerState())
}
enum class TransactionFilter { ALL, NEEDS_REVIEW, INCOME, EXPENSE }
data class TransactionsState(val rows: List<Transaction> = emptyList(), val filter: TransactionFilter = TransactionFilter.ALL)
class TransactionsModel(repository: TransactionRepository) : ScreenModel() {
    private val filter = MutableStateFlow(TransactionFilter.ALL)
    val state = combine(repository.observeAll(), filter) { rows, f ->
        TransactionsState(rows.filter { when (f) {
            TransactionFilter.ALL -> true
            TransactionFilter.NEEDS_REVIEW -> it.status == TransactionStatus.NEEDS_REVIEW
            TransactionFilter.INCOME -> it.type == TransactionType.INCOME
            TransactionFilter.EXPENSE -> it.type == TransactionType.EXPENSE
        } }, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsState())
    fun filter(value: TransactionFilter) { filter.value = value }
}

data class EditorState(
    val original: Transaction? = null, val title: String = "", val amount: String = "",
    val category: String = "Other", val type: TransactionType = TransactionType.EXPENSE,
    val error: String? = null, val ready: Boolean = false, val saving: Boolean = false,
    val sourceText: String? = null, val sourceApp: String? = null, val captureId: Long? = null,
)
class EditorModel(private val repository: TransactionRepository, id: Long,
                  captures: CaptureRepository? = null, captureId: Long? = null) : ScreenModel() {
    private val mutableState = MutableStateFlow(EditorState())
    val state = mutableState.asStateFlow()
    init { work {
        val t = if (id == 0L) null else repository.byId(id)
        mutableState.value = if (id != 0L && t == null) EditorState(error = "Transaction no longer exists.")
        else EditorState(t, t?.title.orEmpty(), t?.let { amountText(it.amountMinor) }.orEmpty(),
            t?.category ?: "Other", t?.type ?: TransactionType.EXPENSE, ready = true)
        if (captureId != null) {
            val capture = captures?.observeLog()?.first()?.find { it.id == captureId }
            mutableState.value = state.value.copy(sourceText = capture?.body, sourceApp = capture?.sourceApp, captureId = capture?.id)
        }
    } }
    fun edit(title: String = state.value.title, amount: String = state.value.amount,
             category: String = state.value.category, type: TransactionType = state.value.type) {
        mutableState.value = state.value.copy(title = title, amount = amount, category = category, type = type, error = null)
    }
    fun save() {
        val s = state.value
        if (!s.ready || s.saving) return
        val amount = parseAmountMinor(s.amount)
        if (s.title.isBlank() || amount == null) {
            mutableState.value = s.copy(error = "Add a description and an amount above zero, with at most two decimal places.")
            return
        }
        mutableState.value = s.copy(saving = true)
        work {
            try {
                repository.upsert(s.original?.copy(title = s.title.trim(), amountMinor = amount,
                    category = s.category, type = s.type, status = TransactionStatus.CONFIRMED)
                    ?: Transaction(title = s.title.trim(), amountMinor = amount, type = s.type,
                        status = TransactionStatus.CONFIRMED, category = s.category,
                        occurredAt = Clock.System.now(), sourceApp = s.sourceApp, captureId = s.captureId))
                mutableEvents.emit(UiEvent.Navigate("transactions"))
            } finally { mutableState.value = state.value.copy(saving = false) }
        }
    }
    fun delete() = work {
        val t = state.value.original ?: return@work
        repository.delete(t.id)
        mutableEvents.emit(UiEvent.Message("Transaction deleted", t))
    }
}

data class SettingsState(val palette: NotiflyPalette = NotiflyPalette.Evergreen, val offline: Boolean = true, val pending: Int = 0)
class SettingsModel(private val preferences: AppPreferences, pending: Flow<Int>) : ScreenModel() {
    val state = combine(preferences.palette, preferences.offline, pending, ::SettingsState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())
    fun palette(value: NotiflyPalette) = work { preferences.setPalette(value) }
    fun offline(value: Boolean) = work {
        if (!value) mutableEvents.emit(UiEvent.Message("Cloud sync is not configured yet. Changes remain saved on this device."))
        else preferences.setOffline(true)
    }
}
data class AllowListState(val apps: List<AllowedApp> = emptyList())
class AllowListModel(private val repository: AllowListRepository) : ScreenModel() {
    val state = repository.observeAll().map(::AllowListState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AllowListState())
    fun toggle(app: AllowedApp) = work { repository.setListening(app.packageName, !app.listening) }
}
data class OnboardingState(val page: Int = 0)
class OnboardingModel : ScreenModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state = mutableState.asStateFlow()
    fun next() { mutableState.value = OnboardingState((state.value.page + 1).coerceAtMost(2)) }
}
data class AuthState(val signup: Boolean = false, val email: String = "", val password: String = "", val error: String? = null)
class AuthModel(private val preferences: AppPreferences, private val demo: Boolean) : ScreenModel() {
    private val mutableState = MutableStateFlow(AuthState())
    val state = mutableState.asStateFlow()
    fun edit(email: String = state.value.email, password: String = state.value.password, signup: Boolean = state.value.signup) {
        mutableState.value = AuthState(signup, email, password)
    }
    fun submit() = work {
        val s = state.value
        if (!s.email.contains('@') || s.password.length < 8) {
            mutableState.value = s.copy(error = "Enter an email and a password of at least 8 characters.")
        } else if (demo) { startOffline() }
        else { mutableState.value = s.copy(error = "Cloud sign-in is not configured. You can continue offline.") }
    }
    fun startOffline() = work {
        preferences.setOffline(true)
        preferences.completeOnboarding()
        mutableEvents.emit(UiEvent.Navigate("home"))
    }
}
