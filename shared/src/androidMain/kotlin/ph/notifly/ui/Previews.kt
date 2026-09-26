package ph.notifly.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.notifly.data.local.appPreferences
import ph.notifly.ui.theme.NotiflyTheme

@Preview(locale = "ar", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun HomeRtlPreview() {
    NotiflyTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            HomeScreen(viewModel { HomeModel(DemoTransactions(), DemoAllowList(), appPreferences(context)) })
        }
    }
}
