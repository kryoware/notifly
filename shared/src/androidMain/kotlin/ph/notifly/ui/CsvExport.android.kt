package ph.notifly.ui

import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

actual fun shareCsv(csv: String) {
    val context = GlobalContext.get().get<Context>()
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_TITLE, "notification-log.csv")
        putExtra(Intent.EXTRA_TEXT, csv)
    }, "Export notification log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
