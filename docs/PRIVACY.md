# Notifly privacy policy

Draft for the current offline Android build. Before publishing, the operator must
add a contact address and effective date and publish this document at a public URL.

Notifly tracks financial transactions locally. Notification access is optional;
manual entry works without it. You choose which installed apps Notifly may read.
The installed-app list is used on your device for this selection and is not uploaded.
For other apps, Notifly records only the app identifier, time, ignored result,
reason, and non-reversible fingerprint;
it does not read their notification text.

Payment notifications are parsed on your device. Parsed transactions always need
your review before they affect the balance. Raw notification text is never sent
to a server or written to diagnostic logs. Raw text retention is off by default.
If enabled, text is stored locally for troubleshooting. Turning retention off
erases stored bodies. Expired captures are removed during maintenance and when
capture or log viewing resumes; operating-system scheduling may delay maintenance.
You can clear the notification log separately from your transactions.

Non-reversible notification fingerprints are retained locally to prevent duplicate
transactions after the log is cleared. They contain no raw text and are removed
when app data is cleared or the app is uninstalled.

The current build does not authenticate to or upload to a cloud service for
transaction sync. Confirmed transaction changes are queued locally for a future
opt-in sync connection. That connection must send only reviewed transaction
fields, never notification bodies, source capture identifiers, or device-only
notes. This policy must be updated with the chosen service, storage region,
retention, and account-deletion procedure before cloud sync is enabled.

Notifly includes an opt-in crash reporter (Sentry), off by default. It does not
initialize until you turn on "Send crash reports" in Settings, and stops
transmitting immediately if you turn it back off. When enabled, a crash or
error report sent to Sentry contains: the exception type, a stack trace, device
model, and OS/app version. It never contains: notification bodies, exception
messages (stripped before sending, since a message can quote parsed
notification text), screenshots, view hierarchies, or transaction data.
Performance tracing and session replay are disabled. Two accepted limitations
of this model: a crash in the first moments of app startup, before your
preference loads, is not captured; and a report already queued on the device
before you turn reporting off may still be delivered once you turn it back on.

There are no advertising SDKs. Android backup is disabled. Uninstalling
the app removes its local data. You can revoke notification access in Android
Settings and disable individual apps in Notifly's allow-list at any time.
