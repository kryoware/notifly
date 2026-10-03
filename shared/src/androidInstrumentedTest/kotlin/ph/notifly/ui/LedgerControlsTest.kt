package ph.notifly.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.runner.RunWith
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.LedgerRepository
import ph.notifly.ui.theme.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class LedgerControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun categoryEditorWaitsForCapAndThenChecksIt() {
        val ledger = DemoLedger(DemoTransactions())
        val model = LedgerSettingsModel(ledger, DemoAllowList())
        var loaded by mutableStateOf(false)
        var cap by mutableStateOf<Long?>(null)
        compose.setContent {
            NotiflyTheme {
                CategoryEditorScreen(model, 0, TransactionType.EXPENSE, cap, monthlyBudgetLoaded = loaded)
            }
        }
        compose.waitUntil { model.state.value.loaded }
        compose.onNodeWithText("Save category").assertDoesNotExist()
        compose.runOnIdle { cap = 1_000; loaded = true }
        compose.onNodeWithText("Name").performTextInput("Coffee")
        compose.onNodeWithText("Monthly budget (PHP, optional)").performTextInput("20")
        compose.onNodeWithText("Save category").assertIsDisplayed().performClick()
        compose.onNodeWithText("Only ₱10.00 of your ₱10.00 monthly budget is unallocated.").assertExists()
        Assert.assertFalse(runBlocking { ledger.observeCategories().first().any { it.name == "Coffee" } })
        compose.runOnIdle { cap = null }
        compose.onNodeWithText("Save category").performClick()
        compose.waitUntil { runBlocking { ledger.observeCategories().first().any { it.name == "Coffee" } } }
    }
    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    private fun screenshotPopup(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun categoryTypesAccountsAndListeningAppsWorkInBothThemes() {
        val transactions = DemoTransactions()
        val ledger = DemoLedger(transactions)
        val apps = DemoAllowList()
        val editor = EditorModel(transactions, 0, ledger = ledger, apps = apps)
        var dark by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (dark) 1.3f else 1f)) {
                NotiflyTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(Modifier.fillMaxSize()) { EditorScreen(editor, mapOf("com.globe.gcash.android" to "GCash", "com.paymaya" to "Maya")) }
                }
            }
        }
        compose.waitUntil { editor.state.value.accounts.isNotEmpty() }
        compose.runOnIdle { editor.edit(title = "Transfer", amount = "25", type = TransactionType.TRANSFER, accountId = 1, toAccountId = 2) }
        compose.onNodeWithText("From account").assertExists()
        compose.onNodeWithText("To account").performScrollTo().assertExists()
        screenshot("ledger-transfer-light.png")
        compose.runOnIdle { dark = true; editor.edit(type = TransactionType.INCOME) }
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Salary").assertExists().performClick()
        compose.runOnIdle { Assert.assertEquals("Salary", editor.state.value.category) }
        compose.onNodeWithText("Source app").performScrollTo().performClick()
        compose.onNodeWithText("GCash").assertExists()
        compose.onNodeWithText("Maya").performClick()
        compose.onNodeWithText("BPI Mobile").assertDoesNotExist()
        screenshot("ledger-income-dark-large.png")
        compose.runOnIdle { editor.edit(type = TransactionType.EXPENSE) }
        compose.runOnIdle { Assert.assertEquals("Other", editor.state.value.category) }
    }
    @Test fun accountsAndCategoryManagementRemainReadableAtLargeText() {
        val ledger = DemoLedger(DemoTransactions())
        val model = LedgerSettingsModel(ledger, DemoAllowList())
        var categories by mutableStateOf(false)
        var dark by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                NotiflyTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(Modifier.fillMaxSize()) {
                        if (categories) CategoriesScreen(model) else AccountEditorScreen(model, 1)
                    }
                }
            }
        }
        compose.waitUntil { model.state.value.loaded }
        compose.onNodeWithText("Name").assertExists()
        screenshot("ledger-account-light-large.png")
        compose.onNodeWithText("Save account").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { categories = true; dark = true }
        compose.onNodeWithText("Food").assertExists()
        screenshot("ledger-categories-dark-large.png")
        compose.onNodeWithText("Income").performClick()
        compose.onNodeWithText("Salary").assertExists()
        compose.onNodeWithText("Food").assertDoesNotExist()
        Assert.assertEquals(2, runBlocking { ledger.observeCategories().first().count { it.name == "Other" } })
    }

    @Test fun searchableEditorChoicesCommitOnlyOnSelection() {
        val transactions = DemoTransactions()
        val editor = EditorModel(transactions, 0, ledger = DemoLedger(transactions), apps = DemoAllowList())
        compose.setContent {
            NotiflyTheme {
                Surface(Modifier.fillMaxSize()) {
                    EditorScreen(editor, mapOf("com.globe.gcash.android" to "GCash"))
                }
            }
        }
        compose.waitUntil { editor.state.value.accounts.isNotEmpty() }

        fun open(title: String) { compose.onNodeWithText(title).performScrollTo().performClick() }
        fun type(query: String) { compose.onNode(isFocused()).performTextInput(query) }
        fun unchanged(account: Long?, destination: Long?, category: String, source: String?) {
            compose.runOnIdle {
                Assert.assertEquals(account, editor.state.value.accountId)
                Assert.assertEquals(destination, editor.state.value.toAccountId)
                Assert.assertEquals(category, editor.state.value.category)
                Assert.assertEquals(source, editor.state.value.sourceApp)
            }
        }

        compose.runOnIdle { editor.edit(title = "Search", amount = "25", accountId = 1, sourceApp = "com.paymaya") }
        open("Account")
        type("  WALLET  ")
        compose.onNodeWithText("GCash wallet").assertExists()
        unchanged(1, null, editor.state.value.category, "com.paymaya")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Maya wallet").assertExists()
        compose.onNode(isFocused()).performImeAction()
        Assert.assertEquals(2, transactions.observeAll().value.size)
        open("Account")
        compose.onAllNodesWithText("GCash wallet").assertCountEquals(2)
        compose.onNodeWithText("Maya wallet").assertExists()
        compose.onNodeWithText("Maya wallet").performClick()
        unchanged(2, null, editor.state.value.category, "com.paymaya")

        compose.runOnIdle { editor.edit(type = TransactionType.TRANSFER, accountId = 1, toAccountId = null) }
        open("From account")
        type("  maYa ")
        compose.onNodeWithText("Maya wallet").assertExists()
        unchanged(1, null, "Transfer", "com.paymaya")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("GCash wallet").assertExists()
        type("  maYa ")
        compose.onNodeWithText("Maya wallet").performClick()
        unchanged(2, null, "Transfer", "com.paymaya")

        open("To account")
        type("MAYA")
        compose.onNodeWithText("No matching accounts").assertExists()
        unchanged(2, null, "Transfer", "com.paymaya")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("GCash wallet").assertExists()
        compose.onNodeWithText("GCash wallet").performClick()
        unchanged(2, 1, "Transfer", "com.paymaya")

        compose.runOnIdle { editor.edit(type = TransactionType.INCOME) }
        open("Category")
        type("  sAL ")
        compose.onNodeWithText("Salary").assertExists()
        compose.onNodeWithText("Food").assertDoesNotExist()
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Other").assertExists()
        type("Salary")
        compose.onAllNodesWithText("Salary").get(1).performClick()
        unchanged(2, null, "Salary", "com.paymaya")

        compose.runOnIdle { editor.edit(type = TransactionType.EXPENSE) }
        open("Category")
        type("salary")
        compose.onNodeWithText("No matching categories").assertExists()
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNode(isFocused()).performImeAction()

        open("Source app")
        type("zz-no-source")
        compose.onNodeWithText("No matching sources").assertExists()
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNodeWithContentDescription("Clear search").performClick()
        type("com.pay")
        compose.onAllNodesWithText("com.paymaya").assertCountEquals(2)
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNodeWithText("com.paymaya").performClick()
        open("Source app")
        type("MAN")
        compose.onNodeWithText("Manual").assertExists()
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNodeWithText("Manual").performClick()
        unchanged(2, null, "Other", null)
    }

    @Test fun capturedTransactionSourceStaysAvailableInPicker() {
        val transactions = DemoTransactions(listOf(Transaction(
            id = 1, title = "Captured", amountMinor = 500, type = TransactionType.EXPENSE,
            status = TransactionStatus.NEEDS_REVIEW, category = "Shopping", occurredAt = kotlin.time.Clock.System.now(),
            sourceApp = "com.paymaya", captureId = 55, accountId = 1,
        )))
        val editor = EditorModel(transactions, 1, ledger = DemoLedger(transactions), apps = DemoAllowList())
        compose.setContent { NotiflyTheme { Surface(Modifier.fillMaxSize()) { EditorScreen(editor) } } }
        compose.waitUntil { editor.state.value.accounts.isNotEmpty() }
        compose.onNodeWithText("Source app").performScrollTo().performClick()
        compose.onAllNodesWithText("com.paymaya").assertCountEquals(2)
        Assert.assertEquals("com.paymaya", editor.state.value.sourceApp)
    }

    @Test fun allSearchableFieldsDiscardQueriesOnDismissDoneAndFocusLoss() {
        val transactions = DemoTransactions()
        val editor = EditorModel(transactions, 0, ledger = DemoLedger(transactions), apps = DemoAllowList())
        lateinit var focusManager: FocusManager
        compose.setContent { NotiflyTheme { Surface(Modifier.fillMaxSize()) {
            focusManager = LocalFocusManager.current
            EditorScreen(editor, mapOf("com.globe.gcash.android" to "GCash"))
        } } }
        compose.waitUntil { editor.state.value.accounts.isNotEmpty() }
        // title, selected label, partial query, matching option, nonmatching option, empty-result noun
        val fields = listOf(
            listOf("Account", "Maya wallet", "  gCa ", "GCash wallet", "Maya wallet", "accounts"),
            listOf("Category", "Other", "  sAL ", "Salary", "Income", "categories"),
            listOf("Source app", "com.paymaya", "  gCa ", "GCash", "Manual", "sources"),
            listOf("From account", "Maya wallet", "  gCa ", "GCash wallet", "Maya wallet", "accounts"),
            listOf("To account", "GCash wallet", "  gCa ", "GCash wallet", "Maya wallet", "accounts"),
        )
        for (field in fields) {
            val (title, selected, query, match, excluded) = field
            val noun = field[5]
            compose.runOnIdle { editor.edit(title = "Search dismissal", amount = "25",
                type = if (title.endsWith("account")) TransactionType.TRANSFER else TransactionType.INCOME,
                accountId = 2, toAccountId = 1, category = "Other", sourceApp = "com.paymaya") }
            val before = editor.state.value
            fun open() { compose.onNodeWithText(title).performScrollTo().performClick() }
            fun unchanged() { compose.runOnIdle { Assert.assertEquals(before, editor.state.value) } }
            open()
            compose.onNode(isFocused()).performTextInput("zz-no-match")
            compose.onNodeWithText("No matching $noun").assertExists()
            unchanged()
            if (title == "Source app") screenshotPopup("search-source-no-matches.png")
            compose.onNodeWithContentDescription("Clear search").performClick()
            compose.onNode(hasText(match).and(hasAnyAncestor(isPopup()))).assertExists()
            unchanged()
            compose.onNode(isFocused()).performTextInput(query)
            compose.onNode(hasText(match).and(hasAnyAncestor(isPopup()))).assertExists()
            compose.onNode(hasText(excluded).and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
            // Selecting the query text must not reset the filter to the entire option list.
            compose.onNode(isFocused()).performTextInputSelection(TextRange(0, query.length))
            compose.onNode(hasText(excluded).and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
            unchanged()
            if (title == "Source app") screenshotPopup("search-source-filtered.png")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNode(isPopup()).assertDoesNotExist()
            compose.onNodeWithText(selected).assertExists()
            unchanged()
            open()
            compose.onNode(hasText(match).and(hasAnyAncestor(isPopup()))).assertExists()
            compose.onNode(isFocused()).performTextInput("uncommitted")
            compose.onNode(isFocused()).performImeAction()
            compose.onNode(isPopup()).assertDoesNotExist()
            compose.onNodeWithText(selected).assertExists()
            unchanged()
            open()
            compose.onNode(isFocused()).performTextInput("uncommitted")
            compose.runOnIdle { focusManager.clearFocus() }
            compose.onNode(isPopup()).assertDoesNotExist()
            compose.onNodeWithText(selected).assertExists()
            unchanged()
        }
        Assert.assertEquals(2, transactions.observeAll().value.size)
    }

    @Test fun emptyChoicesExplainAvailabilityAndKeepCreateAccountAction() {
        val transactions = DemoTransactions()
        val baseLedger = DemoLedger(transactions)
        val ledger = object : LedgerRepository by baseLedger {
            override fun observeAccounts() = kotlinx.coroutines.flow.flowOf(emptyList<Account>())
        }
        val editor = EditorModel(transactions, 0, ledger = ledger, apps = DemoAllowList())
        var emptyCategories by mutableStateOf(false)
        compose.setContent { NotiflyTheme { Surface(Modifier.fillMaxSize()) {
            if (emptyCategories) ChoiceField("Category", "", emptyList<String>(), searchable = true,
                emptyLabel = "categories", label = { it }, choose = { Assert.fail("Empty list cannot select") })
            else EditorScreen(editor)
        } } }
        compose.waitUntil { editor.state.value.ready }
        compose.onNodeWithText("Account").performScrollTo().performClick()
        compose.onNodeWithText("No accounts available").assertExists()
        compose.onNode(isFocused()).performTextInput("missing")
        compose.onNodeWithText("No accounts available").assertExists()
        compose.onNode(isFocused()).performImeAction()
        compose.onNodeWithText("Create an account").assertExists()
        compose.runOnIdle { emptyCategories = true }
        compose.onNodeWithText("Category").performClick()
        compose.onNodeWithText("No categories available").assertExists()
    }

    @Test fun editingRetainsCurrentArchivedChoicesAndExcludesOtherArchivedChoices() {
        val transactions = DemoTransactions(listOf(Transaction(id = 1, title = "Existing transfer",
            amountMinor = 500, type = TransactionType.TRANSFER, status = TransactionStatus.CONFIRMED,
            category = "Transfer", occurredAt = kotlin.time.Clock.System.now(), sourceApp = null, captureId = null,
            accountId = 1, toAccountId = 2)))
        val baseLedger = DemoLedger(transactions)
        val accounts = listOf(Account(1, "Archived from", AccountType.WALLET, archived = true),
            Account(2, "Archived destination", AccountType.WALLET, archived = true),
            Account(3, "Other archived", AccountType.WALLET, archived = true),
            Account(4, "Active wallet", AccountType.WALLET))
        val categories = listOf(Category(1, "Retained category", TransactionType.EXPENSE, archived = true),
            Category(2, "Hidden category", TransactionType.EXPENSE, archived = true),
            Category(3, "Income only", TransactionType.INCOME))
        val ledger = object : LedgerRepository by baseLedger {
            override fun observeAccounts() = kotlinx.coroutines.flow.flowOf(accounts)
            override fun observeCategories() = kotlinx.coroutines.flow.flowOf(categories)
        }
        val editor = EditorModel(transactions, 1, ledger = ledger, apps = DemoAllowList())
        compose.setContent { NotiflyTheme { Surface(Modifier.fillMaxSize()) { EditorScreen(editor) } } }
        compose.waitUntil { editor.state.value.accounts.size == 4 }
        compose.onNodeWithText("From account").performScrollTo().performClick()
        compose.onAllNodesWithText("Archived from").assertCountEquals(2)
        compose.onNode(hasText("Archived destination").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
        compose.onNode(hasText("Other archived").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
        compose.onNode(isFocused()).performImeAction()
        compose.onNodeWithText("To account").performScrollTo().performClick()
        compose.onAllNodesWithText("Archived destination").assertCountEquals(2)
        compose.onNode(hasText("Archived from").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
        compose.onNode(hasText("Other archived").and(hasAnyAncestor(isPopup()))).assertDoesNotExist()
        compose.onNode(isFocused()).performImeAction()
        // A retained legacy category remains searchable even when it is absent from eligible categories.
        compose.runOnIdle { editor.edit(type = TransactionType.EXPENSE, category = "Retained category") }
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNode(isFocused()).performTextInput("  ReTAIN ")
        compose.onNodeWithText("Retained category").assertExists()
        compose.onNodeWithText("Hidden category").assertDoesNotExist()
        compose.onNodeWithText("Income only").assertDoesNotExist()
        compose.onNodeWithText("Retained category").performClick()
        Assert.assertEquals("Retained category", editor.state.value.category)
    }

    @Test fun searchableLongAccountMenuScrollsAtLargeTextInBothThemes() {
        val transactions = DemoTransactions()
        val accounts = (1..24).map { Account(it.toLong(), "Account $it · ${"Bank and savings wallet ".repeat(2)}", AccountType.WALLET) }
        val baseLedger = DemoLedger(transactions)
        val ledger = object : LedgerRepository by baseLedger {
            override fun observeAccounts() = kotlinx.coroutines.flow.flowOf(accounts)
        }
        val editor = EditorModel(transactions, 0, ledger = ledger, apps = DemoAllowList())
        var dark by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                NotiflyTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface(Modifier.fillMaxSize()) {
                        EditorScreen(editor)
                    }
                }
            }
        }
        compose.waitUntil { editor.state.value.accounts.size == accounts.size }
        compose.runOnIdle { editor.edit(title = "Long account search", amount = "25", accountId = 1) }
        compose.onNodeWithText("Account").performScrollTo().performClick()
        compose.onNode(isPopup().and(hasAnyDescendant(hasScrollAction()))).assertExists()
        compose.waitForIdle()
        screenshotPopup("search-menu-light-large.png")
        compose.runOnIdle { dark = true }
        compose.waitForIdle()
        screenshotPopup("search-menu-dark-large.png")
        val longName = accounts.last().name
        compose.onNode(isFocused()).performTextInput("Account 24")
        compose.onNodeWithText(longName).assertExists().performClick()
        compose.runOnIdle { Assert.assertEquals(24L, editor.state.value.accountId) }
    }
}
