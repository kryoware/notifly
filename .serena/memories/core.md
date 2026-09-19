# Notifly — core

Android-first personal finance tracker. Transactions come from parsing
notifications of allow-listed apps, on-device. KMP + Compose Multiplatform.

**`CLAUDE.md` (auto-loaded) holds the non-negotiable invariants** (privacy, money
as `Long` centavos, NEEDS_REVIEW defaults, colour source, platform boundary).
Do not duplicate them here — read it, don't re-derive it.

## Serena tooling status

Kotlin language server **fails to start in this project** (`initialize` →
`LanguageServerTerminatedException`). All symbolic tools (`get_symbols_overview`,
`find_symbol`, `find_referencing_symbols`, `replace_symbol_body`, …) error out.
Fall back to Read/Grep/Edit until it is fixed. Logs: `~/.serena/logs/<date>/`.
Cause unconfirmed; `JAVA_HOME` is the Android Studio JBR (JDK 25), possibly
newer than the language server supports.

## Source map

- `app/` — Android entry point only. `MainActivity`, `NotiflyApplication` (Koin
  start), `service/NotificationCaptureService` (the NotificationListenerService).
- `shared/src/commonMain/ph/notifly/`
  - `domain/model/` — `Transaction`, `RawCapture`, `AllowedApp` + enums
    (`TransactionType`, `TransactionStatus`, `CaptureResult`).
  - `domain/repository/` — the 3 repo interfaces. No implementations yet.
  - `domain/source/TransactionSource` — THE platform boundary interface.
  - `data/parser/` — `NotificationParser` + `ParseOutcome`/`TransactionDraft`.
    Only real logic in the repo today.
  - `ui/theme/` — `Color.kt` (generated from Figma, do not hand-edit),
    `Theme.kt`, `Type.kt`. `ui/NotiflyApp.kt` is a placeholder screen.
  - `di/Modules.kt` — `sharedModule` + `expect val androidModule`.
- `shared/src/androidMain/` — `NotificationTransactionSource`, Koin actual.
- `shared/src/iosMain/` — `IosTransactionSource` (reports unavailable, correct),
  Koin actual. No `iosApp/` Xcode project exists and none should be created.
- `shared/src/commonTest/` — `NotificationParserTest`, the parser regression net.

## State of the build

Greenfield. Missing on purpose: Room entities/DAOs/`AppDatabase`, all repository
implementations, ViewModels, every screen past the placeholder.
`shared/build/` already holds generated Compose resource collectors — a Gradle
configuration/build has partially run, so the tree is not untouched.
The capture→ledger path is severed at `NotificationCaptureService` ~line 87:
captures are recorded, but the `TODO: persist the Transaction itself via
TransactionRepository` means no `Transaction` is ever created yet.

Phased checklist with checkboxes lives in `PLAN.md` (phases 0–10); it is the
authoritative task order. `docs/prototype.html` is the behavioural spec for
every screen — open it in a browser rather than guessing interactions.

## Further memories

- Languages, plugins, dependency catalog and its unverified-version trap:
  `mem:tech_stack`
- Gradle/test/build commands, Windows shell specifics, wrapper facts:
  `mem:suggested_commands`
- Code style, KMP source-set rules, parser/theme/DI patterns and naming traps:
  `mem:conventions`
- What to run and what to grep for before calling a change done:
  `mem:task_completion`
