package ph.notifly.domain.source

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ph.notifly.domain.repository.CaptureRepository

class NotificationTransactionSource(private val context: Context, private val captures: CaptureRepository) : TransactionSource {
    override val id = "android.notification-listener"
    private val mutableConnection = MutableStateFlow("Disconnected")
    override val connection = mutableConnection.asStateFlow()
    override fun isAvailable(): Boolean = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners",
    ).orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).any {
        it.packageName == context.packageName
    }
    override fun observe() = captures.observeLog()
        .let { flow -> kotlinx.coroutines.flow.flow { flow.collect { rows -> rows.firstOrNull()?.let { emit(it) } } } }
    fun connected(value: Boolean) { mutableConnection.value = if (value) "Connected" else "Disconnected" }
    fun storageError() { mutableConnection.value = "Capture failed — check device storage, then reconnect" }
}
