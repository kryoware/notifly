# Notifly

Android-first personal finance tracker. Transactions are created by parsing
notifications from apps the user explicitly allow-lists. Kotlin Multiplatform +
Compose Multiplatform.

## Non-negotiables

- **Notification text never leaves the device.** Parse on-device. Only confirmed
  transactions sync. Raw bodies must never reach a network payload.
- **Never log notification content**, even at DEBUG. Package names and counts only.
- **Parsed transactions default to NEEDS_REVIEW.** Never auto-confirm.
- **NEEDS_REVIEW never moves the headline balance.** Show it as a separate pending line.
- **Never invent a value to avoid an Unrecognized branch.** No amount means no transaction.
- **Money is `Long` minor units (centavos).** Never `Double`, never `Float`.
- **All colour comes from `MaterialTheme.colorScheme` or `MaterialTheme.accents`.**
  No hardcoded hex outside `ui/theme/Color.kt`.

Platform-rendered launcher and Android 12 splash assets are the sole scoped
exception: they may use Android system black/white resources because they are
outside Compose and cannot access `MaterialTheme`.

## Platform boundary

Notification capture is **Android-only**. iOS has no API for reading other apps'
notifications — not a restricted entitlement, simply absent. A Notification
Service Extension only sees this app's own pushes.

All capture goes through `TransactionSource` in `commonMain/domain/source`.
Never reference `NotificationListenerService`, `Context`, or any `android.*`
type outside `androidMain`. `IosTransactionSource` reporting unavailable is
correct behaviour, not a stub to fill in with fake captures.

There is no `iosApp/` yet. The iOS targets are declared so `commonMain` stays
honest. Do not create an Xcode project until the iOS capture story is decided
(bank aggregator, file import, or manual only).

## Structure

```
app/      Android entry point + NotificationCaptureService
shared/   commonMain: domain, data, ui (Compose), di
          androidMain: notification source, DB builder, Koin actuals
          iosMain: unavailable source, DB builder, Koin actuals
          commonTest: parser regression suite
```

Layering is Data → Domain → Presentation. Domain is pure Kotlin with no platform
imports. Repositories are interfaces in domain, implemented in data.

## First run

**Versions in `gradle/libs.versions.toml` are unverified.** They were written
offline and never resolved against a repository. Expect some to be wrong.

```
./gradlew :shared:compileKotlinAndroid
./gradlew :shared:allTests
./gradlew :app:assembleDebug
```

Fix version resolution failures first, then compilation, then tests. Do not
change parser behaviour to make a test pass — the tests encode intended
behaviour and were written before the implementation was verified.

Missing on purpose: Gradle wrapper (run `gradle wrapper`), launcher icons,
Room entities/DAOs/database, repository implementations, ViewModels, and all
screens beyond a placeholder.

## Build order

Full phase-by-phase plan with checkboxes: `PLAN.md`. Short version:

1. Get Gradle resolving and `:shared:allTests` green.
2. Room entities, DAOs, `AppDatabase`, platform DB builders.
3. Repository implementations + Koin wiring.
4. Theme check — render all four palettes side by side.
5. Screens against seeded fake data: home, transactions, CRUD, settings, log.
6. Wire `NotificationCaptureService` to `TransactionRepository`.
7. Permission flow (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` + return check).
8. Sync queue (WorkManager on Android).

Build the UI against fake data *before* the service. You do not want to debug a
listener that only fires when a real notification arrives.

## Gotchas already handled in code

- Notification access is special access; there is no permission dialog.
- The listener binding drops on app update — call `requestRebind()` and surface
  real connection state rather than assuming it is alive.
- Apps update one notification in place, so the same `sbn.key` arrives repeatedly.
  `NotificationCaptureService` dedupes on key + content hash.
- `QUERY_ALL_PACKAGES` needs a declared Play Store justification.

## Design source

Figma file key `BTqrcTY3MPDY5WeDng5dzi` — `M3 Color` roles alias `M3 Tones`.
`ui/theme/Color.kt` is generated from it. Change colours in Figma, regenerate here.

Interaction reference: `docs/prototype.html` — open it in a browser. It is the
behavioural spec for every screen, including the notification log and the
offline sync ring.
