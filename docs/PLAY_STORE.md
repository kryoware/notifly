# Play Store submission notes

## Notification access disclosure

Notifly uses notification access to identify payments from financial apps explicitly
selected by the user. It extracts the amount, merchant, and payment direction on the
device. Every result requires review; raw text is never uploaded. Permission can
be declined or revoked without losing manual-entry functionality.

Show this disclosure before opening Android's notification-access settings. Record
a demonstration of granting access, selecting an app, receiving a payment alert,
reviewing the draft, disabling capture, and deleting the log.

## Package visibility justification

The allow-list picker needs installed application labels and package identifiers so
the user can choose any payment-producing app, including regional banks and wallets.
The list stays on the device and is not used for advertising or profiling.

`QUERY_ALL_PACKAGES` approval is not guaranteed. If Google Play rejects broad
visibility, scope package queries to supported apps and document that limitation.
Do not claim approval until the permission declaration has been reviewed.

## Data Safety section

Declare *Crash logs* and *Diagnostics* under App activity: collected, not shared
with third parties beyond the processor (Sentry), user can request deletion,
encrypted in transit. Mark both optional — the crash reporter is opt-in and off
by default, and Google requires this declaration regardless of opt-in status.
Do not declare Personal info, Financial info, or Messages as collected: those
never leave the device (see `PRIVACY.md`).

## Release gates

- Publish the final privacy-policy URL and operator contact information.
- Supply signing credentials through the existing CI secrets; never commit a keystore.
- Install and run the minified release on a real device. Verify Room, DataStore,
  Koin initialization, CRUD, permission return, listener rebind, and app upgrades.
- Test GCash and Maya delivery, repeated updates, disabled apps, raw-text retention,
  delete/undo, airplane mode, and process restarts.
- Review all four palettes against Figma, including contrast and large text.
- Test TalkBack focus order and labels, 48dp touch targets, and the Arabic RTL preview.
- Cloud auth/sync and confidence tiers are not shipping features until their remaining
  PLAN.md gates are complete. The offline build must not advertise successful sync.
