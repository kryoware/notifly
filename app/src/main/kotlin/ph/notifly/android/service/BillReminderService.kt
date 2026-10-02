package ph.notifly.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.android.ext.android.inject
import ph.notifly.android.MainActivity
import ph.notifly.data.local.AppPreferences
import ph.notifly.domain.diagnostics.ErrorReporter
import ph.notifly.domain.diagnostics.ErrorSite
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.repository.BillRepository
import ph.notifly.ui.money
import kotlin.time.Clock

/**
 * Posts due-date reminders for confirmed bills, once per occurrence.
 * Notification text carries the bill name only, plus the amount unless amounts are hidden or a PIN is set.
 */
class BillReminderService : JobService() {
    private val bills: BillRepository by inject()
    private val preferences: AppPreferences by inject()
    private val reporter: ErrorReporter by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val retry = try { withContext(Dispatchers.IO) { remind() }; false }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { reporter.report(e, ErrorSite.CAPTURE_MAINTENANCE); true }
            jobFinished(params, retry)
        }
        return true
    }

    private suspend fun remind() {
        val lead = preferences.billReminderDays.first() ?: return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val quiet = preferences.hideAmounts.first() || preferences.pinSet.first()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT))
        bills.observeBills().first().filter { it.status == TransactionStatus.CONFIRMED }.forEach { bill ->
            val due = bill.nextDue ?: return@forEach
            val days = due.toEpochDays() - today.toEpochDays()
            if (days > lead || days < 0 || bill.remindedFor == due) return@forEach
            val whenText = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }
            val open = PendingIntent.getActivity(this, bill.id.toInt(),
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "bills").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val public = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("A bill is due $whenText").build()
            manager.notify(bill.id.toInt(), Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("${bill.name} due $whenText")
                .setContentText(if (quiet) "Tap to mark it paid" else "${money(bill.amountMinor)} · Tap to mark it paid")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setContentIntent(open).setAutoCancel(true).build())
            bills.markReminded(bill.id, due)
        }
    }

    override fun onStopJob(params: JobParameters): Boolean { job?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private companion object { const val CHANNEL = "bill_reminders" }
}
