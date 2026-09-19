package ph.notifly.domain.source

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ph.notifly.domain.model.RawCapture

/**
 * Android capture source. The actual OS callback lives in the app module's
 * NotificationCaptureService, which pushes into [emit].
 *
 * Notification access is a SPECIAL ACCESS, not a runtime permission. You cannot
 * request it with a dialog — send the user to Settings and verify on return.
 */
class NotificationTransactionSource(
    private val context: Context,
) : TransactionSource {

    override val id: String = "android.notification-listener"

    private val captures = MutableSharedFlow<RawCapture>(extraBufferCapacity = 64)

    override fun isAvailable(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.contains(context.packageName)
    }

    override fun observe(): Flow<RawCapture> = captures.asSharedFlow()

    internal fun emit(capture: RawCapture) {
        captures.tryEmit(capture)
    }

    companion object {
        val SETTINGS_ACTION: String = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    }
}
