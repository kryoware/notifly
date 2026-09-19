# Build plan

Working plan for Claude Code. Rules and constraints live in `CLAUDE.md` — read
that first; this file is only the order of work.

Behavioural spec for every screen: `docs/prototype.html`. Open it in a browser
and use it, don't just read it. When this plan says "match the prototype", it
means the interaction, not a pixel copy.

**Tick boxes as you go.** Each phase is roughly one session. Don't start a phase
until the previous one's Done check passes.

---

## Phase 0 — Make it build

Nothing else matters until Gradle resolves. The versions in
`gradle/libs.versions.toml` were written offline and never resolved; expect
several to be wrong.

- [ ] `gradle wrapper` (the wrapper is not committed)
- [ ] `./gradlew help` resolves — fix version catalog entries until it does
- [ ] `./gradlew :shared:compileKotlinAndroid` compiles
- [ ] `./gradlew :shared:allTests` runs (failures are fine here, errors are not)
- [ ] `./gradlew :app:assembleDebug` produces an APK
- [ ] App launches on a device and shows the placeholder screen

Fix in that order: resolution, then compilation, then tests. Record any version
you changed and why, in the commit message.

**Done when:** the placeholder renders on a real device.

---

## Phase 1 — Parser green

The tests encode intended behaviour and were written before the implementation
was verified. **If a test fails, fix the parser, not the test** — unless you can
articulate why the expectation itself is wrong.

- [ ] All nine tests in `NotificationParserTest` pass
- [ ] Add a case for an amount with no decimals and a thousands separator (`PHP 1,200`)
- [ ] Add a case where two amounts appear and the larger is the balance, not the transaction
- [ ] Confirm `toMinorUnits` never touches `Double`

**Done when:** `./gradlew :shared:allTests` is green.

---

## Phase 2 — Persistence

- [ ] Room entities: `TransactionEntity`, `RawCaptureEntity`, `AllowedAppEntity`
- [ ] DAOs with `Flow` returns; never expose entities past the repository
- [ ] `AppDatabase` + `expect`/`actual` builders in `androidMain` / `iosMain`
- [ ] Mappers entity ↔ domain model
- [ ] Schema exported to `shared/schemas/`
- [ ] Instrumented or in-memory DB test: insert, query by status, delete

Store amounts as `INTEGER` minor units. Store instants as epoch millis.

**Done when:** a test writes a transaction and reads it back as a domain model.

---

## Phase 3 — Repositories + DI

- [ ] Implement the three repository interfaces in `data/repository`
- [ ] `observeConfirmedNetMinor()` excludes `NEEDS_REVIEW` — assert this in a test
- [ ] `CaptureRepository.redactBodies()` blanks bodies in place
- [ ] `purgeExpired()` drops captures older than `RawCapture.RETENTION_HOURS`
- [ ] Wire all of it into `sharedModule`
- [ ] Fakes for each repository in `commonTest`

**Done when:** Koin starts without unresolved dependencies and fakes exist for testing.

---

## Phase 4 — Theme check

Cheap, and catches token mistakes before they're baked into twenty screens.

- [ ] A debug screen or `@Preview` rendering all four palettes side by side
- [ ] Swatches for every role plus `accents.income` / `accents.expense`
- [ ] Compare against the Theming page in the Figma file
- [ ] Palette choice persists via DataStore

**Done when:** all four palettes render and match Figma.

---

## Phase 5 — Screens on fake data

Build against seeded fakes. Do **not** touch the listener yet — you don't want
to debug a service that only fires when a real notification arrives.

Navigation first, then screens in this order:

- [ ] Nav graph: onboarding → auth → main (bottom nav) → detail/edit → log
- [ ] Home — balance card, review prompt, recent list
- [ ] Transactions — filter chips (All / Needs review / Income / Expense)
- [ ] Add / Edit — validation rejects empty description and non-positive amount
- [ ] Delete — confirmation dialog, undo via snackbar
- [ ] Settings — allow-list entry, offline switch, theme picker
- [ ] Allow-list — per-app toggles
- [ ] Onboarding + permission slides
- [ ] Auth — login / signup, plus "continue offline"

One ViewModel per screen. `StateFlow<UiState>` for state, `SharedFlow<Event>`
for navigation and snackbars. Never put navigation in `UiState`.

**Done when:** every flow in the prototype works against fake data.

---

## Phase 6 — Notification log

Build this before wiring the real listener. It's the only debugging surface once
captures start arriving, and you'll want it working first.

- [ ] Log screen listing captures newest-first
- [ ] Four result states, colour-coded (Parsed / Needs review / Not recognised / Ignored)
- [ ] Raw body with the matched amount and direction keyword highlighted
- [ ] Expand shows amount, direction, merchant, source, plain-language reason
- [ ] "Create transaction manually" on unrecognised entries, prefilled with the source text
- [ ] "Keep raw text on device" switch → calls `redactBodies()` when turned off
- [ ] Clear log action

**Done when:** you can seed a fake capture and see it rendered correctly.

---

## Phase 7 — Real capture

- [ ] `NotificationCaptureService` writes through `CaptureRepository`
- [ ] Parsed drafts become transactions with `NEEDS_REVIEW` — never auto-confirm
- [ ] Permission flow: `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`, then verify
      on resume via `NotificationTransactionSource.isAvailable()`
- [ ] Handle the user returning with it still disabled
- [ ] `requestRebind()` on reconnect; show real connection state in Settings
- [ ] Allow-list populated from installed apps (`QUERY_ALL_PACKAGES`)
- [ ] Verify dedupe against an app that updates one notification repeatedly
- [ ] Grep the codebase for any log statement that could carry notification text

Test with GCash and Maya on a real device. Emulators won't give you real
notification traffic.

**Done when:** a real payment notification lands in the app as Needs review.

---

## Phase 8 — Offline and sync

- [ ] Every write goes to Room first; no network in the write path
- [ ] Pending-change queue with a count
- [ ] WorkManager sync worker on Android, constrained to connectivity
- [ ] Progress ring on the account avatar, driven by the queue draining
- [ ] Green ring + check on completion (match the prototype)
- [ ] Confirmed transactions only in the sync payload — assert this in a test

**Done when:** airplane mode → edits → reconnect drains the queue visibly.

---

## Phase 9 — Confidence tiers

Deferred deliberately; needs real capture data from Phase 7 to tune.

- [ ] Duplicate detection: same amount + near-same timestamp across two apps
- [ ] "Merge or keep both?" prompt
- [ ] Transfer handling excluded from spending totals
- [ ] Pre-auth holds reconciled when the final amount posts
- [ ] Per-app learned rules, inspectable and deletable from the allow-list

---

## Phase 10 — Ship

- [ ] Release build with R8, verify nothing reflective breaks
- [ ] Play Store justification for `QUERY_ALL_PACKAGES` and notification access
- [ ] Privacy policy stating on-device parsing explicitly
- [ ] Launcher icons, adaptive icon, splash
- [ ] Accessibility pass: TalkBack labels, 48dp targets, contrast
- [ ] `@Preview(locale = "ar")` RTL check

---

## Not doing yet

**iOS.** The targets are declared so `commonMain` stays honest, but there's no
`iosApp/` and shouldn't be until the iOS capture story is decided. iOS cannot
read other apps' notifications; it needs a bank aggregator, file import, or
manual entry. That's a product decision, not a coding task.

---

## First session prompt

> Read CLAUDE.md and PLAN.md. Work Phase 0 only. Get `./gradlew :app:assembleDebug`
> succeeding — fix version resolution first, then compilation. Tell me every
> version you changed and why. Don't start Phase 1.
