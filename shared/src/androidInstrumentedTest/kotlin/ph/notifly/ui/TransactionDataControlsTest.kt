package ph.notifly.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ph.notifly.data.transfer.TransactionCsv
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.repository.TransactionRepository
import ph.notifly.ui.theme.NotiflyTheme
import ph.notifly.ui.theme.ThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class TransactionDataControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportedFileCanBeImportedAfterConfirmationAndDemoDisablesActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "transaction-roundtrip.csv")
        val actions = mutableListOf<String>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                input: I, options: ActivityOptionsCompat?) {
                actions.add(contract.createIntent(context, input).action!!)
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val source = DemoTransactions()
        var repository by mutableStateOf<TransactionRepository>(source)
        var demo by mutableStateOf(false)
        var dark by mutableStateOf(false)
        val messages = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (dark) 1.3f else 1f)) {
                    NotiflyTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                        Column { TransactionDataControls(repository, demo) { messages.add(it) } }
                    }
                }
            }
        }
        try {
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.getExternalFilesDir(null), "import-export-ready.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithText("Export transactions").performClick()
            compose.waitUntil(10_000) { messages.contains("Transactions exported.") }
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, actions.last())
            assertEquals(2, TransactionCsv.decode(file.readText()).size)
            val destination = DemoTransactions()
            val initial = runBlocking { destination.observeAll().first() }
            compose.runOnIdle { repository = destination }
            compose.onNodeWithText("Import transactions").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Import 2 transactions?").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, actions.last())
            compose.runOnIdle { dark = true }
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                File(context.getExternalFilesDir(null), "import-export-review.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            assertEquals(initial, runBlocking { destination.observeAll().first() })
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(initial, runBlocking { destination.observeAll().first() })
            compose.onNodeWithText("Import transactions").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Import").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Import", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { messages.any { it.startsWith("2 transactions imported") } }
            val imported = runBlocking { destination.observeAll().first() }.filter { it.id !in initial.map { row -> row.id } }
            assertEquals(2, imported.size)
            assertTrue(imported.all { it.status == TransactionStatus.NEEDS_REVIEW })
            assertEquals(initial.filter { it.status == TransactionStatus.CONFIRMED }.sumOf {
                when (it.type) { ph.notifly.domain.model.TransactionType.INCOME -> it.amountMinor
                    ph.notifly.domain.model.TransactionType.EXPENSE -> -it.amountMinor
                    ph.notifly.domain.model.TransactionType.TRANSFER -> 0L }
            }, runBlocking { destination.observeConfirmedNetMinor().first() })
            compose.runOnIdle { demo = true }
            compose.onNodeWithText("Import transactions").assertHasNoClickAction()
            compose.onNodeWithText("Export transactions").assertHasNoClickAction()
        } finally { file.delete() }
    }
}
