package ph.notifly.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.model.*
import ph.notifly.domain.repository.LedgerRepository
import ph.notifly.ui.theme.*
import java.io.File
import okio.IOException
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class HomeAccountsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val transactions = DemoTransactions()
    private val accountRows = MutableStateFlow((1L..9L).map { Account(it, "Account $it", AccountType.WALLET,
        balanceMinor = it * 10000L, balanceAsOf = Clock.System.now()) })
    private val ledger = object : LedgerRepository by DemoLedger(transactions) {
        override fun observeAccounts() = accountRows
        override fun observeDrafts() = flowOf(emptyList<CapturedDraft>())
    }
    private val store = object : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var fail = false
        override val data = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (fail) throw IOException("write failed")
            return transform(state.value).also { state.value = it }
        }
    }
    private val prefs = AppPreferences(store)
    private val model = HomeModel(transactions, DemoAllowList(), prefs, ledger)

    private fun start() {
        compose.setContent {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(model) { model.events.collect { if (it is UiEvent.Message) snackbar.showSnackbar(it.text) } }
            NotiflyTheme { HomeScreen(model, snackbar = snackbar) }
        }
        compose.waitUntil { model.state.value.loaded }
        compose.onNodeWithText("Reorder").performScrollTo().performClick()
    }

    private fun grid() = compose.onNode(hasScrollAction())
    private fun show(text: String) { grid().performScrollToNode(hasText(text)) }
    private fun handles() = compose.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true)
    private fun waitOrder(vararg ids: Long) = compose.waitUntil {
        runBlocking { prefs.homeAccountOrder.first() }.take(ids.size) == ids.toList()
    }
    private fun handle(name: String): Offset {
        val tile = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
        return handles().fetchSemanticsNodes().single { tile.contains(it.boundsInRoot.center) }.boundsInRoot.center
    }
    private fun dragHandle(from: String, to: String) {
        val bounds = grid().fetchSemanticsNode().boundsInRoot
        val start = handle(from) - bounds.topLeft
        val end = handle(to) - bounds.topLeft
        grid().performTouchInput { swipe(start, end, durationMillis = 700) }
        compose.waitForIdle()
    }
    private fun move(name: String, action: String) {
        val actions = compose.onNodeWithText(name).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { Assert.assertTrue(actions.first { it.label == action }.action()) }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun crossColumnRowAccessibleMovesDoneBackAndReopening() {
        start()
        grid().performScrollToIndex(2)
        dragHandle("Account 1", "Account 2")
        waitOrder(2, 1, 3)
        dragHandle("Account 2", "Account 3")
        waitOrder(1, 3, 2)
        move("Account 2", "Move earlier")
        waitOrder(1, 2, 3)
        move("Account 2", "Move later")
        waitOrder(1, 3, 2)
        compose.onNodeWithText("Account 1").assertHasNoClickAction()
        show("Done")
        compose.onNodeWithText("Done").performClick()
        show("Account 1")
        compose.onNodeWithText("Account 1").performClick()
        compose.onNodeWithText("Account 1 balance").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        show("Reorder")
        compose.onNodeWithText("Reorder").performClick()
        compose.onNodeWithText("Done").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Reorder").assertExists()
        waitOrder(1, 3, 2)
        Assert.assertEquals(listOf(1L, 3L, 2L), runBlocking { AppPreferences(store).homeAccountOrder.first() }.take(3))
        compose.onNodeWithText("Reorder").performClick()
        compose.onNodeWithText("Done").assertExists()
        compose.runOnIdle { store.fail = true }
        show("Account 2")
        move("Account 2", "Move earlier")
        compose.onNodeWithText("Couldn't save or load data. Please try again.").assertExists()
        waitOrder(1, 3, 2)
        compose.runOnIdle { Assert.assertEquals(listOf(1L, 3L, 2L), model.state.value.accounts.take(3).map { it.account.id }) }
    }

    @Test fun holdingAtBottomAndTopEdgesScrollsAndSaves() {
        start()
        grid().performScrollToIndex(2)
        val bounds = grid().fetchSemanticsNode().boundsInRoot
        val start = handle("Account 1") - bounds.topLeft
        compose.mainClock.autoAdvance = false
        val downAt = SystemClock.uptimeMillis()
        fun touch(action: Int, point: Offset) {
            compose.runOnUiThread {
                MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, point.x, point.y, 0).let {
                    compose.activity.dispatchTouchEvent(it)
                    it.recycle()
                }
            }
        }
        touch(MotionEvent.ACTION_DOWN, start + bounds.topLeft)
        touch(MotionEvent.ACTION_MOVE, Offset(start.x, bounds.height - 20f) + bounds.topLeft)
        // Advance frames while keeping the finger still at the edge.
        compose.mainClock.advanceTimeBy(1600)
        touch(MotionEvent.ACTION_UP, Offset(start.x, bounds.height - 20f) + bounds.topLeft)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        Assert.assertTrue("Saved order: ${runBlocking { prefs.homeAccountOrder.first() }}",
            runBlocking { prefs.homeAccountOrder.first() }.indexOf(1L) >= 4)
        val below = runBlocking { prefs.homeAccountOrder.first() }.indexOf(1L)
        val nextBounds = grid().fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithText("Account 1").fetchSemanticsNode().boundsInRoot
        val grip = handles().fetchSemanticsNodes().first { it.boundsInRoot.center.y in handle.top..handle.bottom && it.boundsInRoot.center.x in handle.left..handle.right }
        compose.mainClock.autoAdvance = false
        touch(MotionEvent.ACTION_DOWN, grip.boundsInRoot.center)
        touch(MotionEvent.ACTION_MOVE, Offset(start.x, 20f) + nextBounds.topLeft)
        compose.mainClock.advanceTimeBy(1600)
        touch(MotionEvent.ACTION_UP, Offset(start.x, 20f) + nextBounds.topLeft)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.waitUntil { runBlocking { prefs.homeAccountOrder.first() }.indexOf(1L) < below }
    }

    @Test fun countsThemesLargeTextAndHiddenAmounts() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        accountRows.value = emptyList()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                NotiflyTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { HomeScreen(model) }
            }
        }
        compose.waitUntil { model.state.value.loaded }
        compose.onNodeWithText("Accounts").assertDoesNotExist()
        compose.runOnIdle { accountRows.value = listOf(Account(1, "One wallet", AccountType.WALLET)) }
        compose.onNodeWithText("One wallet").performScrollTo().assertExists()
        compose.onNodeWithText("Reorder").assertDoesNotExist()
        compose.runOnIdle {
            accountRows.value = listOf(
                Account(1, "A very long account name for everyday spending", AccountType.WALLET,
                    balanceMinor = 9_000_000_000L, balanceAsOf = Clock.System.now()),
                Account(2, "Travel card", AccountType.CARD, balanceMinor = 345_678L, balanceAsOf = Clock.System.now(), archived = true),
                Account(3, "Negative bank balance", AccountType.BANK, balanceMinor = -987_654L, balanceAsOf = Clock.System.now()))
        }
        compose.onNodeWithText("Reorder").performScrollTo()
        screenshot("home-accounts-light.png")
        compose.runOnIdle { dark = true; scale = 1.5f }
        compose.onNodeWithText("Travel card").performScrollTo().assertExists()
        compose.onNodeWithText("Debt owed").assertExists()
        compose.onNodeWithText("Archived").assertExists()
        screenshot("home-accounts-dark-large.png")
        show("Negative bank balance")
        compose.onNodeWithText("Negative bank balance").assertExists()
        screenshot("home-accounts-odd-negative.png")
        compose.runOnIdle { model.hideAmounts(true) }
        compose.waitUntil { model.state.value.hideAmounts }
        compose.onAllNodesWithContentDescription("Amount hidden").onFirst().assertExists()
        show("Negative bank balance")
        compose.onNodeWithText("Negative bank balance").assertExists()
        screenshot("home-accounts-hidden.png")
    }
}
