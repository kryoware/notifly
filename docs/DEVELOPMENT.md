# Development rules

Shared instructions for every agent. `AGENTS.md` and `CLAUDE.md` are identical
entry points; edit both together. Maintain detailed rules here or in linked docs.

## Non-negotiables

- Notification text never leaves the device. Parse on-device, only for explicitly
  allow-listed apps; only confirmed transactions may sync. Never send raw bodies.
- Never log notification content, including at DEBUG. Package names and counts only.
- Parsed transactions default to `NEEDS_REVIEW`; never auto-confirm. Pending rows
  never change the headline balance; show them as a separate pending line.
- No amount means no transaction. Never invent a value to avoid `Unrecognized`.
- Money is `Long` minor units (centavos), never `Double` or `Float`.
- Never modify existing transactions during device tests: devices may hold real
  data. Create clearly marked `[TEST]` transactions and delete only those afterwards.
- Compose colours come from `MaterialTheme.colorScheme` or `MaterialTheme.accents`.
  No hardcoded app hex outside [Color.kt](../shared/src/commonMain/kotlin/ph/notifly/ui/theme/Color.kt).
  The sole platform exception is launcher/Android 12 splash assets: they may use
  Android system black/white and generated `notifly_*` resources in
  [colors.xml](../app/src/main/res/values/colors.xml). Never hand-edit generated colours.

## Platform boundary

Notification capture is Android-only. iOS has no API for other apps' notifications;
a Notification Service Extension sees only this app's own pushes.

All capture goes through [TransactionSource](../shared/src/commonMain/kotlin/ph/notifly/domain/source/TransactionSource.kt).
Keep `NotificationListenerService`, `Context`, and all `android.*` types in Android
source sets (`app/` or `shared/src/androidMain/`), never shared domain code.
`IosTransactionSource` correctly reports capture unavailable; do not fake it.
iOS targets keep shared code platform-neutral. Do not create `iosApp/` or an Xcode
project until the capture approach is decided: bank aggregator, file import, or manual only.

## Structure and capture

- `app/`: Android entry point and notification services.
- `shared/src/commonMain/`: domain, data, Compose UI, and DI.
- `shared/src/androidMain/` and `iosMain/`: platform sources, DB builders, and Koin actuals.
- `shared/src/commonTest/`: shared regressions; Android unit/instrumented tests live
  in `androidUnitTest/` and `androidInstrumentedTest/`.
- Data → Domain → Presentation. Domain is pure Kotlin without platform imports;
  repository interfaces live in domain, implementations in data.
- Build UI against seeded fake data before integrating live notification capture.
- Notification access is special access, not a runtime permission dialog. Open
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` and verify on return.
- Listener bindings can drop on app updates. Use `requestRebind()` and show actual
  connection state. Notifications update in place: preserve key/content fingerprint
  dedupe through the source/repository flow, including repeated deliveries.
- `QUERY_ALL_PACKAGES` requires [Play Store justification](PLAY_STORE.md).

## Build and verification

Use the committed Gradle wrapper with JDK 17 and SDK versions from
[libs.versions.toml](../gradle/libs.versions.toml). On Windows use `./gradlew.bat`.
Fix dependency resolution failures before compilation and test failures.

```sh
./gradlew :shared:testDebugUnitTest :app:assembleDebug :app:lintDebug
git diff --check
```

- Run shared tests when shared logic changes; Android assembly compiles shared
  Android code too. For release changes, also run `:app:assembleRelease`.
- `:shared:allTests` includes iOS targets; iOS compilation/tests require macOS tooling.
- Parser regression tests encode intended behaviour. Never change parser behaviour
  merely to make a test pass; fix the implementation to meet the intended result.
- For capture/log/sync changes, check diagnostics for notification text and verify
  neither raw bodies nor `NEEDS_REVIEW` rows enter network payloads.
- For device tests, screenshots, and interaction, follow the claiming rules
  in [EMULATORS.md](EMULATORS.md). Never clear existing ledger data to prepare tests.
- Verify UI on a device and against the available product/design references;
  report any unperformed checks. Update [PLAN.md](../PLAN.md) only for completed work.
- Existing implementation and remaining gates are in
  [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md); do not treat old setup notes
  as evidence that the wrapper, Room, repositories, icons, or screens are missing.

## UI and design sources

- [DESIGN.md](../DESIGN.md) defines the visual system; local `Color.kt` is authoritative
  for runtime tokens. Figma file `BTqrcTY3MPDY5WeDng5dzi` remains a visual reference.
- Regenerate tokens and launcher colours with `python tools/generate_colors.py`
  using Python `material-color-utilities==0.2.6`. Keep this dependency outside app
  runtime; retain the generator's configured palette seeds and Ube brand swatches.
- Use generated Material Symbols, never hand-drawn icons or `Icons.Default`;
  follow [MD_ICONS.md](MD_ICONS.md) and `tools/download_symbols.py`.
- All bulk-action lists follow [MD3_LIST.md](MD3_LIST.md). `TransactionsScreen` /
  `TransactionRow` are the reference, including contextual selection, animated row
  tint, avatar check crossfade, swipe actions, and accessibility `customActions`.
  Theme colour rules above override any sample colours in external guides.
- [PRODUCT.md](../PRODUCT.md) describes product behaviour. The previously referenced
  interaction spec `docs/prototype.html` was deleted from this branch; report
  prototype-dependent checks as unavailable. If restored, open it in a browser to
  verify screen interactions, including the notification log and offline sync ring.
