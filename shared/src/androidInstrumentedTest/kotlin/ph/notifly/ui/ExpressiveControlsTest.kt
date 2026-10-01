package ph.notifly.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import ph.notifly.data.local.AppPreferences
import ph.notifly.ui.theme.NotiflyTheme

@RunWith(AndroidJUnit4::class)
class ExpressiveControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedTransactionTogglesOncePerRowClick() {
        val transaction = runBlocking { DemoTransactions().byId(2)!! }
        var selected by mutableStateOf(false)
        var calls = 0
        compose.setContent {
            NotiflyTheme {
                TransactionRow(transaction, emptyMap(), {}, selectionMode = true, selected = selected,
                    onToggleSelection = { calls++; selected = !selected })
            }
        }
        compose.onNodeWithContentDescription("SM Supermarket, ₱2,450.50, needs review").performClick()
        compose.runOnIdle { assertEquals(1, calls); assertEquals(true, selected) }
    }

    @Test fun collapsedFabKeepsItsName() {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(emptyPreferences())
        }
        val model = DemoTransactions().let { HomeModel(it, DemoAllowList(), AppPreferences(store), DemoLedger(it)) }
        compose.setContent { NotiflyTheme { HomeScreen(model) } }
        compose.waitUntil { model.state.value.rows.isNotEmpty() }
        compose.onNodeWithContentDescription("Add transaction").assertExists()
        compose.onNode(hasScrollAction()).performScrollToIndex(3)
        compose.onNodeWithContentDescription("Add transaction").assertExists()
    }

    @Test fun hideAmountsToggleObscuresHomeFigures() {
        val store = object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
                transform(state.value).also { state.value = it }
        }
        val model = DemoTransactions().let { HomeModel(it, DemoAllowList(), AppPreferences(store), DemoLedger(it)) }
        compose.setContent { NotiflyTheme { HomeScreen(model) } }
        val full = "SM Supermarket, ₱2,450.50, needs review"
        compose.waitUntil { model.state.value.rows.isNotEmpty() }
        compose.onNodeWithContentDescription(full).assertExists()
        compose.onNodeWithContentDescription("Hide amounts").performClick()
        compose.waitUntil { model.state.value.hideAmounts }
        compose.onNodeWithContentDescription(full).assertDoesNotExist()
        compose.onNodeWithContentDescription("SM Supermarket, needs review").assertExists()
        compose.onAllNodesWithContentDescription("Amount hidden").onFirst().assertExists()
        compose.onNodeWithContentDescription("Hide amounts").performClick()
        compose.waitUntil { !model.state.value.hideAmounts }
        compose.onNodeWithContentDescription(full).assertExists()
    }

    @Test fun editorShowsFieldErrorsOnFields() {
        val model = DemoTransactions().let { EditorModel(it, 0, ledger = DemoLedger(it), apps = DemoAllowList()) }
        compose.setContent { NotiflyTheme { EditorScreen(model) } }
        compose.waitUntil { model.state.value.ready }
        compose.onNodeWithText("Save transaction").performClick()
        compose.onNodeWithText("Add a description.").assertExists()
        compose.onNodeWithText("Enter an amount above zero, with at most two decimal places.").assertExists()
    }

    @Test fun authShowsFieldErrorsOnFields() {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(emptyPreferences())
        }
        val model = AuthModel(AppPreferences(store), demo = true)
        compose.setContent { NotiflyTheme { AuthScreen(model, demo = true) } }
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Enter a valid email.").assertExists()
        compose.onNodeWithText("Use at least 8 characters.").assertExists()
    }

    @Test fun timePickerCancelPreservesValueAndConfirmUpdatesIt() {
        var time by mutableStateOf("14:30")
        var updates = 0
        compose.setContent {
            NotiflyTheme {
                DateTimeFields("2026-09-26", time, onDate = {}, onTime = { time = it; updates++ })
            }
        }
        compose.onNodeWithContentDescription("Choose time").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("14:30", time); assertEquals(0, updates) }
        compose.onNodeWithContentDescription("Choose time").performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals("14:30", time); assertEquals(1, updates) }
    }

    @Test fun bulkDeleteRequiresConfirmation() {
        val model = DemoTransactions().let { TransactionsModel(it, DemoLedger(it)) }
        compose.setContent { NotiflyTheme { TransactionsScreen(model) } }
        compose.waitUntil { model.state.value.rows.isNotEmpty() }
        compose.onNodeWithContentDescription("SM Supermarket, ₱2,450.50, needs review")
            .performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Delete selected transactions").performClick()
        compose.onNodeWithText("Delete 1 transactions?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("SM Supermarket").assertExists()
    }

    @Test fun logCardAnnouncesExpandedState() {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(emptyPreferences())
        }
        val model = LogModel(DemoCaptures(), AppPreferences(store))
        compose.setContent { NotiflyTheme { LogScreen(model) } }
        compose.waitUntil { model.state.value.captures.isNotEmpty() }
        compose.onNodeWithText("GCash · Parsed")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            .performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }
}
