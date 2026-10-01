# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Individuals in the Philippines (amounts in ₱) tracking their own money. They already get a notification for every bank or e-wallet movement; they glance at the phone between other tasks and want those pings turned into a ledger without typing. Primary job: review auto-drafted transactions and confirm them.

## Product Purpose

Android-first personal finance tracker. Transactions are created by parsing notifications from apps the user explicitly allow-lists. Success is a trustworthy balance with almost no data entry: every captured ping becomes a draft, and the user's confirmation is the only thing that makes it count.

## Positioning

Zero-effort capture: money events are read from notifications the user already receives, so the ledger fills itself. Supporting facts, not the lead: parsing is on-device, notification text never leaves the phone, and nothing counts toward the balance until the user confirms it.

## Operating Context

- Capture is Android-only (NotificationListenerService). iOS has no API to read other apps' notifications; iOS targets exist only to keep `commonMain` honest, and the iOS source reports unavailable by design.
- Notification access is special access, not a permission dialog. The listener binding can drop on app update, so real connection state is surfaced.
- OEM process-killers can stop capture; onboarding offers an optional battery-optimisation step.
- Apps often update one notification in place; capture dedupes on key + content hash.
- Review loop: drafts land in NEEDS_REVIEW, user confirms, only then does the headline balance move. Pending shows as a separate line.

## Capabilities and Constraints

- Notification text never leaves the device; only confirmed transactions sync. Never log notification content.
- Parsed transactions default to NEEDS_REVIEW; never auto-confirm. No amount means no transaction.
- Money is `Long` minor units (centavos).
- Manual entry remains available. Onboarding has no login/signup gate; offline use is first-class. Cloud auth is not yet configured.
- Features present: home with balance and review prompt, transactions list with bulk actions, add/edit, allow-list, notification log with parse results, insights, app lock, account balances, transfer detection, theme palettes.
- Open decision: iOS capture story (bank aggregator, file import, or manual only).
- Open decision: Play Store release; `QUERY_ALL_PACKAGES` needs a declared justification.

## Brand Commitments

Binding brand identity is "Ube" (`docs/brand/run-2026-09-30-ube/brandkit.png`):
- Name: notifly. Mark: lowercase "n" arch with a check flick.
- Voice: "Nothing counts until you do." "The last stroke is yours." "Every ping, checked." "Read on-device · counted by you."
- Pending is an open circle, confirmed is a check; that pair is the product's core semantic.
- Palette seeds Night #110D17, Halaya #3A1F66, Ube #7443E6, Lilac #CBB6FF, Gata #F4F0F8.
- Type: Geist (display/UI), Fraunces (voice), Geist Mono (labels).
- Implementation rule: all colour from `MaterialTheme.colorScheme` / `MaterialTheme.accents`; no hex outside `ui/theme/Color.kt`.

## Evidence on Hand

- Brandkit board, marks, CSS and JS tokens: `docs/brand/run-2026-09-30-ube/`.
- Onboarding concept screens and motion storyboard: `docs/design/onboarding-2026-10-01/`.
- Behavioural prototype: `docs/prototype.html`. Build order: `PLAN.md`. Icon guide: `docs/MD_ICONS.md`. List pattern: `docs/MD3_LIST.md`.
- No user testimonials, usage data, or store listing exist; do not fabricate them.

## Product Principles

1. The user's confirmation is the source of truth; automation only drafts.
2. Privacy is structural, not a setting: text stays on device.
3. Never invent a value to make capture look successful; unrecognised stays unrecognised.
4. Show real state (listener connected or not, pending vs confirmed) rather than assuming.
5. Reviewing must be fast enough to do in a glance.

## Accessibility & Inclusion

State is never colour-only: permission and pending/confirmed states use words and icons together. Icon controls keep accessible labels; swipe actions provide `customActions`. Honour system font scale and Remove animations.
