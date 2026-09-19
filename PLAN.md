# Build plan

Working plan for Claude Code. Rules and constraints live in `CLAUDE.md` — read
that first; this file is only the order of work.

Behavioural spec for every screen: `docs/prototype.html`. Open it in a browser
and use it, don't just read it. When this plan says "match the prototype", it
means the interaction, not a pixel copy.

## Phase 4 — Theme check

Cheap, and catches token mistakes before they're baked into twenty screens.

- [x] A debug screen or `@Preview` rendering all four palettes side by side
- [x] Swatches for every role plus `accents.income` / `accents.expense`
- [ ] Compare against the Theming page in the Figma file
- [x] Palette choice persists via DataStore

**Done when:** all four palettes render and match Figma.

---

## Phase 5 — Screens on fake data

Build against seeded fakes. Do **not** touch the listener yet — you don't want
to debug a service that only fires when a real notification arrives.

Navigation first, then screens in this order:

- [x] Nav graph: onboarding → auth → main (bottom nav) → detail/edit → log
- [x] Home — balance card, review prompt, recent list
- [x] Transactions — filter chips (All / Needs review / Income / Expense)
- [x] Add / Edit — validation rejects empty description and non-positive amount
- [x] Delete — confirmation dialog, undo via snackbar
- [x] Settings — allow-list entry, offline switch, theme picker
- [x] Allow-list — per-app toggles
- [x] Onboarding + permission slides
- [x] Auth — login / signup, plus "continue offline"

Implementation checks pass for seeded demo flows. Cloud auth is not yet configured;
production sign-in reports this explicitly. Full device interaction review remains pending.

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
