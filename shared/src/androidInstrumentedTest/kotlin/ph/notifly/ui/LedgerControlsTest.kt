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
        compose.onNodeWithText("Save category").performScrollTo().performClick()
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
}
