package ph.notifly.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
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
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save account"))
        compose.onNodeWithText("Save account").assertIsEnabled()
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
        compose.onNodeWithText("com.paymaya").assertExists()
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNodeWithText("com.paymaya").performClick()
        open("Source app")
        type("MAN")
        compose.onNodeWithText("Manual").assertExists()
        unchanged(2, null, "Other", "com.paymaya")
        compose.onNodeWithText("Manual").performClick()
        unchanged(2, null, "Other", null)
    }

    @Test fun capturedTransactionSourceStaysReadOnly() {
        val transactions = DemoTransactions(listOf(Transaction(
            id = 1, title = "Captured", amountMinor = 500, type = TransactionType.EXPENSE,
            status = TransactionStatus.NEEDS_REVIEW, category = "Shopping", occurredAt = kotlin.time.Clock.System.now(),
            sourceApp = "com.paymaya", captureId = 55, accountId = 1,
        )))
        val editor = EditorModel(transactions, 1, ledger = DemoLedger(transactions), apps = DemoAllowList())
        compose.setContent { NotiflyTheme { Surface(Modifier.fillMaxSize()) { EditorScreen(editor) } } }
        compose.waitUntil { editor.state.value.accounts.isNotEmpty() }
        compose.onNodeWithText("Source: com.paymaya").performScrollTo().assertExists()
        compose.onNodeWithText("Source app").assertDoesNotExist()
        Assert.assertEquals("com.paymaya", editor.state.value.sourceApp)
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
