package ph.notifly.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.runner.RunWith
import ph.notifly.data.local.AppPreferences
import ph.notifly.ui.theme.*

@RunWith(AndroidJUnit4::class)
class NavigationRefinementsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themeChoicesFillWidthAndBecomeRadioRowsAtLargeText() {
        var large by mutableStateOf(false)
        var selected by mutableStateOf(ThemeMode.SYSTEM)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                NotiflyTheme {
                    Box(Modifier.width(if (large) 280.dp else 320.dp)) { ThemeModeChoices(selected) { selected = it } }
                }
            }
        }
        val normal = listOf("System", "Light", "Dark").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        Assert.assertEquals(normal[0].width, normal[1].width, 2f)
        Assert.assertEquals(normal[1].width, normal[2].width, 2f)
        Assert.assertEquals(normal[0].top, normal[1].top, 2f)
        compose.runOnIdle { large = true }
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onAllNodes(radio).assertCountEquals(3)
        val rows = listOf("System", "Light", "Dark").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        Assert.assertTrue(rows[0].top < rows[1].top && rows[1].top < rows[2].top)
        compose.onNodeWithText("Dark").assertIsDisplayed().performClick()
        compose.runOnIdle { Assert.assertEquals(ThemeMode.DARK, selected) }
    }

    @Test fun reminderDialogSavesEachChoiceAndCancelPreservesIt() {
        var days by mutableStateOf<Int?>(null)
        var requests = 0
        compose.setContent { NotiflyTheme { BillReminderRow(days, false, { requests++ }) { days = it } } }
        listOf("On the day" to 0, "1 day before" to 1, "3 days before" to 3, "Off" to null).forEach { (label, value) ->
            compose.onNodeWithText("Bill reminders").performClick()
            compose.onNodeWithText(label).performClick()
            compose.runOnIdle { Assert.assertEquals(value, days) }
            compose.onNodeWithText("Cancel").assertDoesNotExist()
        }
        compose.runOnIdle { Assert.assertEquals(3, requests) }
        compose.onNodeWithText("Bill reminders").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { Assert.assertNull(days) }
        compose.onNodeWithText("Bill reminders").performClick()
        compose.onNodeWithText("1 day before").performClick()
        compose.onNodeWithText("1 day before · Notifications blocked").assertExists()
    }

    @Test fun logExportIsHiddenInReleasePresentation() {
        val store = object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(state.value).also { state.value = it }
        }
        val model = LogModel(DemoCaptures(), AppPreferences(store))
        var debug by mutableStateOf(false)
        compose.setContent { NotiflyTheme { LogScreen(model, isDebugBuild = debug) } }
        compose.waitUntil { model.state.value.captures.isNotEmpty() }
        compose.onNodeWithContentDescription("Export CSV").assertDoesNotExist()
        compose.runOnIdle { debug = true }
        compose.onNodeWithContentDescription("Export CSV").assertExists()
    }

    @Test fun feedbackSitsAboveStickySave() {
        val snackbar = SnackbarHostState()
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
                NotiflyTheme {
                    StickyActionScaffold(actions = {
                        Button(onClick = { scope.launch { snackbar.showSnackbar("Saved") } }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Save") }
                    }) { modifier -> Box(modifier) }
                }
            }
        }
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()
        compose.onNodeWithText("Saved").assertIsDisplayed()
        val message = compose.onNodeWithText("Saved").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithText("Save").fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(message.bottom < save.top)
    }
}
