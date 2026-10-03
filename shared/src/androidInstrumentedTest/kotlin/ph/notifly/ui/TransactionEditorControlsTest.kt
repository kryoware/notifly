package ph.notifly.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ph.notifly.domain.model.Account
import ph.notifly.domain.model.AccountType
import ph.notifly.ui.theme.NotiflyTheme

@RunWith(AndroidJUnit4::class)
class TransactionEditorControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dateAndTimeShareRowAndAccountSearchMatchesLastFour() {
        var selected by mutableStateOf<Long?>(null)
        val accounts = listOf(Account(1, "Bank savings", AccountType.BANK, lastFour = "1234"),
            Account(2, "Visa card", AccountType.CARD, lastFour = "5678"))
        compose.setContent {
            NotiflyTheme {
                Column(Modifier.fillMaxWidth()) {
                    DateTimeFields("2026-10-03", "14:30", {}, {})
                    AccountPicker("Account", selected, accounts) { selected = it }
                }
            }
        }
        val date = compose.onNodeWithText("Date").fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("Time").fetchSemanticsNode().boundsInRoot
        assertEquals(date.top, time.top)
        assertTrue(date.right <= time.left)
        compose.onNode(hasSetTextAction() and hasText("Account")).performClick().performTextReplacement("5678")
        compose.onNodeWithText("Bank savings · Ending 1234").assertDoesNotExist()
        compose.onNodeWithText("Visa card · Ending 5678").performClick()
        compose.runOnIdle { assertEquals(2L, selected) }
    }

    @Test fun largeTextDateAndTimeRemainInOneScrollableRow() {
        compose.setContent {
            NotiflyTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Column(Modifier.width(280.dp)) { DateTimeFields("2026-10-03", "14:30", {}, {}) }
                }
            }
        }
        compose.onNodeWithText("2026-10-03").assertIsDisplayed()
        compose.onNodeWithText("Time").performScrollTo().assertIsDisplayed()
        val date = compose.onNodeWithText("Date").fetchSemanticsNode().boundsInRoot
        val time = compose.onNodeWithText("Time").fetchSemanticsNode().boundsInRoot
        assertEquals(date.top, time.top)
    }

    @Test fun categoryAndSourceFilterCaseInsensitivelyAndEmptySearchRecovers() {
        var category by mutableStateOf("Other")
        var source by mutableStateOf<String?>(null)
        compose.setContent {
            NotiflyTheme {
                Column {
                    ChoiceField("Category", category, listOf("Other", "Food", "Transport"),
                        searchable = true, label = { it }) { category = it }
                    ChoiceField("Source app", source ?: "Manual", listOf<String?>(null, "GCash", "Maya"),
                        searchable = true, label = { it ?: "Manual" }) { source = it }
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Category")).performClick().performTextReplacement("FOO")
        compose.onNodeWithText("Transport").assertDoesNotExist()
        compose.onNodeWithText("Food").performClick()
        compose.runOnIdle { assertEquals("Food", category) }
        compose.onNode(hasSetTextAction() and hasText("Source app")).performClick().performTextReplacement("missing")
        compose.onNodeWithText("No matching options").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Source app")).performTextReplacement("may")
        compose.onNodeWithText("GCash").assertDoesNotExist()
        compose.onNodeWithText("Maya").performClick()
        compose.runOnIdle { assertEquals("Maya", source) }
    }
}
