# Conventions

Style baseline is `kotlin.code.style=official`; no linter/formatter is wired
into the build. Match surrounding code rather than reformatting.

## Layering

Data → Domain → Presentation. `domain/` is pure Kotlin: no `android.*`, no
Compose, no Room imports. Repository **interfaces** live in
`domain/repository`, implementations belong in `data/repository` (none exist
yet). DAOs/entities must never escape past a repository — map to domain models.

## KMP source sets

- `commonMain` must stay platform-free; the only sanctioned seam is
  `domain/source/TransactionSource`.
- Naming trap: `expect val androidModule` in `commonMain/di/Modules.kt` has an
  `actual` in **both** `androidMain` and `iosMain`. The name says "android" but
  it is the generic platform module. Do not add a parallel `iosModule`.

## Documentation style

KDoc on a type explains *why the rule exists*, in the user's language, not what
the code does — e.g. `RawCapture` carries the privacy contract, `Transaction`
explains what each status does to the balance. Preserve those blocks when
editing; they are the spec. Inline comments are rare and mark hazards
(notification dedupe, record-before-parse ordering).

## Parser

- `NotificationParser` is rule lists + regex, no ML, no network. Direction comes
  from keyword lists (`inbound`/`outbound`/`holdWords`/`balanceWords`/
  `selfTransferHints`); extend the lists rather than adding branches.
- `ParseOutcome` is a sealed interface: `Parsed(draft, reason)` or
  `Unrecognized(reason)`. `reason` is plain language shown to the user in the
  notification log — write it for a human, not a developer.
- Per-field `Confidence` (amount/direction/merchant). `TransactionDraft.needsReview`
  is derived: low amount or direction confidence, or `TRANSFER`.
- `toMinorUnits` is string maths only. Never introduce `Double`/`Float` into a
  money path, including intermediates.

## Compose / theming

- Four palettes in `NotiflyPalette` (Evergreen, Indigo, Slate, Clay), each with
  a `lightColorScheme` in `Color.kt` plus a `NotiflyAccents` (income/expense)
  record, because MD3 has no income/expense roles. Reach them via
  `MaterialTheme.accents` (a `staticCompositionLocalOf`), never a literal.
- `Color.kt` is generated from Figma (file key `BTqrcTY3MPDY5WeDng5dzi`);
  change colours there and regenerate. No dark schemes exist yet — adding them
  needs a second tonal ramp in Figma, not hand-picked hexes.

## Tests

`commonTest` uses `kotlin.test` with backticked descriptive test-function names.
`NotificationParserTest` mirrors the seeded log in `docs/prototype.html`; add a
case there for every real-world misparse.
