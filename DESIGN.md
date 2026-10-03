---
name: Notifly
description: A quiet violet ledger where every captured ping waits as an open ring until you check it.
colors:
  ube: "#7443E6"
  lilac: "#CBB6FF"
  halaya: "#3A1F66"
  halaya-deep: "#26134A"
  mist: "#E8DEFF"
  gata: "#F4F0F8"
  night: "#110D17"
  canvas: "#0A0810"
  plum: "#1A1422"
  plum-raised: "#221A2D"
  plum-high: "#2D2439"
  mute: "#A39AB6"
  dim: "#6F6683"
  line: "#3A2F4C"
  stone-violet: "#5F5675"
  error: "#BA1A1A"
  error-dark: "#FFB4AB"
typography:
  display:
    fontFamily: "Fraunces, Georgia, serif"
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "Geist, system-ui, sans-serif"
    fontWeight: 600
    letterSpacing: "-0.035em"
    fontFeature: "tnum"
  title:
    fontFamily: "Geist, system-ui, sans-serif"
    fontWeight: 500
    letterSpacing: "-0.015em"
  body:
    fontFamily: "Geist, system-ui, sans-serif"
    fontWeight: 400
  label:
    fontFamily: "Geist, system-ui, sans-serif"
    fontWeight: 500
  stamp:
    fontFamily: "Geist Mono, ui-monospace, monospace"
    fontWeight: 500
    letterSpacing: "0.12em"
rounded:
  chip: "10dp"
  fab: "20dp"
  tray: "18dp"
  card: "24dp"
  hero: "28dp"
  full: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  xxl: "24dp"
components:
  balance-hero:
    backgroundColor: "{colors.halaya}"
    textColor: "{colors.gata}"
    rounded: "{rounded.hero}"
    padding: "22dp"
  review-tray:
    backgroundColor: "{colors.night}"
    textColor: "{colors.gata}"
    rounded: "{rounded.tray}"
    padding: "8dp 8dp 8dp 14dp"
  button-primary:
    backgroundColor: "{colors.lilac}"
    textColor: "{colors.halaya-deep}"
    rounded: "{rounded.full}"
    padding: "0 16dp"
  chip-selected:
    backgroundColor: "{colors.halaya}"
    textColor: "{colors.lilac}"
    rounded: "{rounded.chip}"
  transaction-row:
    backgroundColor: "{colors.night}"
    textColor: "{colors.gata}"
    typography: "{typography.title}"
  fab:
    backgroundColor: "{colors.lilac}"
    textColor: "{colors.halaya-deep}"
    rounded: "{rounded.fab}"
    size: "60dp"
---

# Design System: Notifly

## Overview

**Creative North Star: "The Checked Receipt"**

Every notification becomes a slip that waits. It sits as an open ring, uncounted, until the user stamps it with a check. The whole system is built to make that single transition legible and satisfying: open ring to filled tick, pending line to headline balance. Everything else is quiet so the transition reads.

Ube is a violet-on-night world. The brand is dark-first: deep plum surfaces stepping up in small tonal increments, with Lilac as the one light in the room. The app follows the system light/dark setting by default; light theme is the same hues inverted onto Gata (a warm off-white), a first-class theme rather than a separate identity. Voice lives in Fraunces: italic serif on taglines and empty states against Geist everywhere else. Numbers are Geist with tabular figures, so money columns align and the cents sit dimmer than the pesos.

The system is Material 3 Expressive in structure (navigation bar, ListItem, Material buttons, snackbars) with the brand expressed through colour roles, type, shape and motion, never through custom controls. Tone: calm, confident, private. It is not loud, glossy, or gamified.

**Key Characteristics:**
- Pending is an outlined ring; confirmed is a filled tick. This pair is the product's core semantic and never changes meaning.
- Tonal depth, flat at rest. Shadows are rare.
- Soft, rounded, tactile: pill buttons, 24dp cards, 10dp chips.
- Money is tabular, signed, and split: pesos at full weight, centavos dimmed.
- Fraunces appears only as brand voice; it is never used for data.
- Four palettes ship (Ube, Evergreen, Slate, Clay). Ube is the identity and the only palette used on brand surfaces (launcher icon, splash, store art). The others are user themes: they must satisfy the same roles and pass `ColorContrastTest`, but nothing is specified against them.

## Colors

A single violet hue family on near-black plum, with one pale lilac light and no second accent hue.

### Primary
- **Ube** (#7443E6): Primary in light theme; the confirmed tick fill in both themes; brand mark swatch. Strong, saturated, used sparingly on a screen.
- **Lilac** (#CBB6FF): Primary in dark theme: filled buttons, FAB, income amounts, selected-state text, links. The one light in dark UI.
- **Halaya** (#3A1F66): Primary container in dark theme (balance hero, selected chip and nav pill); secondary container.
- **Halaya Deep** (#26134A): Text and icons on Lilac fills; on-primary-container in light.
- **Mist** (#E8DEFF): Primary container in light theme.

### Neutral
- **Night** (#110D17): Dark surface and background; also the expense amount colour in light theme.
- **Canvas** (#0A0810): Lowest container in dark; the deepest well.
- **Plum** (#1A1422), **Plum Raised** (#221A2D), **Plum High** (#2D2439): Dark surface container steps; cards and trays climb this ladder instead of casting shadows.
- **Gata** (#F4F0F8): Light background and surface; on-surface text in dark.
- **Mute** (#A39AB6): Secondary text and icons in dark.
- **Dim** (#6F6683): Outline in dark; de-emphasised cents and tertiary meta.
- **Line** (#3A2F4C): Outline variant, dividers and chip borders in dark.
- **Stone Violet** (#5F5675): On-surface-variant in light.

### Semantic
- **Error** (#BA1A1A light / #FFB4AB dark): Material error roles only, for real failures. Expense is not error and is not red.

### Named Rules
**The Ring-and-Tick Rule.** Pending is always an outlined ring in the tertiary role; confirmed is always a filled circle in `accents.confirmed` with a check. Never express status by colour alone; the words "Needs review" accompany the ring.

**The One Light Rule.** In dark theme, Lilac is the only bright colour. If a screen has more than a few Lilac moments, demote some to Gata or Mute.

**The No-Red-Ink Rule.** Income is Lilac (dark) / Ube (light); expense is Gata (dark) / Night (light); both signed with + and −. Spending is not an alarm. Red is reserved for errors and destructive confirmation.

**The Roles-Only Rule.** All colour comes from `MaterialTheme.colorScheme` or `MaterialTheme.accents`. No hex outside `ui/theme/Color.kt`.

## Typography

**Display Font:** Fraunces (Georgia fallback), regular and italic
**Body Font:** Geist (system sans fallback), regular / medium / semibold
**Label/Mono Font:** Geist Mono, medium

**Character:** Geist is a clean, slightly technical grotesque that makes numbers calm. Fraunces is the human voice: an italic serif that says "you do" and "checked." The contrast between them is the brand.

### Hierarchy
- **Display** (Fraunces, Material display scale, −0.025em): Brand lines only: onboarding statements and taglines ("Nothing counts until you do."). Not screen titles: "Welcome back" is Geist `headlineMedium`.
- **Voice** (`typography.voice`, Fraunces italic 26/32sp): Empty-state titles and single spoken lines.
- **Balance** (`typography.balance`, Geist SemiBold, −0.035em, tabular, from `headlineLarge`): The confirmed balance and insight totals. On the hero it auto-sizes 24sp to 44sp to fit on one line.
- **Headline** (Geist, Material scale): Screen and auth titles, keypad digits.
- **Title** (Geist Medium, −0.015em): Screen titles, row titles, amounts in lists.
- **Body** (Geist Regular): Descriptions, supporting text, explanations.
- **Label** (Geist Medium): Buttons, chips, nav labels.
- **Stamp** (Geist Mono Medium, +0.12em, uppercase): `labelSmall` only; receipt-style metadata and state tags.

### Named Rules
**The Tabular Money Rule.** Every peso amount uses `tabular()` (`tnum`) so columns line up. Centavos are dimmed (about 55% content colour) via `splitMoney`.

**The Voice-Not-Data Rule.** Fraunces never sets numbers, labels, controls, or screen headings. It is voice only: taglines and empty states.

## Layout

Single-column phone layout, 16dp side gutters, content in vertical stacks with 12dp to 16dp rhythm between groups and 22dp padding inside hero surfaces.

Padding and gaps use the 4dp `Space` scale: `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 20, `xxl` 24. Off-grid values (10, 14, 18, 22) are allowed only where they reproduce a measured brand-sheet value: tray insets, chip padding, hero padding. Component sizes (avatars, icons, touch targets) are sizes, not spacing, and stay literal dp. Navigation is a Material navigation bar on compact width with a single FAB for the primary action; rail or drawer on expanded widths per Material guidance. Edge-to-edge with window insets applied. Touch targets are 48dp minimum (confirm control is a 48dp box around a 22dp ring).

Lists use Material `ListItem` with a 40dp leading avatar, title and supporting line, and trailing signed amount plus status mark. The headline balance sits above the review prompt; pending is always its own line, never merged into the total.

## Elevation & Depth

Tonal layering, flat at rest. Depth is the climb from Canvas to Night to Plum to Plum Raised to Plum High in dark theme, and the matching surfaceContainer steps in light. The balance hero is a filled Halaya container, not a shadowed card. The FAB is the only element that takes a soft shadow. Dialogs and sheets use Material defaults. Visual mood comes from a faint halftone dot or noise grain on brand surfaces (onboarding, print), never on data screens.

### Named Rules
**The Flat-By-Default Rule.** Surfaces carry no shadow at rest. Raise a surface by stepping its container colour, not by adding a shadow.

## Shapes

Rounded and friendly with a few distinct tiers: chips 10dp, FAB 20dp, review tray 18dp, cards 24dp, hero container 28dp (Material extra-large), buttons and nav pills fully round.

These map to `MaterialTheme.shapes`: `small` = chip (10), `medium` = card (24), `large` = FAB (20), `extraLarge` = hero (28, Material default), `tray` = review tray (18). `large` is smaller than `medium` on purpose: Material's Card reads `medium` and the extended FAB reads `large`, so the scale follows the components, not size order. Corners are always continuous circular arcs; nothing is sharp, nothing is squircle-skewed. The brand mark is a stroked arch with round caps and a 50° check flick, and its geometry (round, single stroke) is the pattern for icons: Material Symbols, rounded, never hand-drawn and never `Icons.Default`.

## Components

### Buttons
- **Shape:** Pill (fully rounded).
- **Primary:** Lilac fill with Halaya Deep text in dark; Ube fill with Gata text in light. 16dp horizontal padding minimum.
- **Tonal / Text:** Plum High or secondary container fill; text buttons in Lilac. Material states and ripple apply.
- **FAB:** One only, 20dp radius, Lilac fill. Never stack FABs.

### Chips
- **Style:** 10dp radius, 1dp Line border at rest; selected fills with Halaya and Lilac text, border removed.
- **State:** Filter chips (All / Needs review / Income / Expense).

### Cards / Containers
- **Corner Style:** 24dp (cards), 28dp (balance hero).
- **Background:** Surface container steps; hero uses primaryContainer.
- **Shadow Strategy:** None; see Elevation.
- **Internal Padding:** 18dp to 22dp.

### Inputs / Fields
- **Style:** Material 3 defaults, themed through colour roles. Validation rejects empty description and non-positive amount with inline error text.

### Navigation
- **Style:** Material navigation bar on Plum; active item uses a Halaya pill with Lilac icon and Gata label. Contextual top app bar for selection: close X, count, actions, `surfaceVariant` container.

### Transaction Row (signature)
Avatar (40dp, app icon or initial) crossfades to a check-circle when selected. Title in Title style, supporting line "Category · Date · Needs review" with the review words in tertiary. Trailing: signed tabular amount (`accents.income` / `accents.expense`) then the status mark. Selected row tints primaryContainer at about 30% via `animateColorAsState`. Swipe actions via `SwipeToDismissBox` with `customActions`.

### Status Mark (signature)
Open ring (tertiary, 2dp stroke, 64% of box) when pending; filled `accents.confirmed` (Ube) circle with a Gata check when confirmed. Decorative; rows announce status in words.

### Balance Hero with Review Tray (signature)
Halaya container with "Confirmed balance" label and split-money headline. Below it, only when there are drafts, an inset tray (18dp radius, surface at about 35% alpha) holding the pending ring, "N to review", "−₱X · not counted", and a Review button. The tray grows with `animateContentSize`.

### Motion
Material motion patterns with the system's remove-animations setting honoured. Brand movements are restrained: a pending ring breathes gently, a tick appears only after confirmation, slips settle into the tray with a soft bounce. Never decorative loops on data screens.

## Do's and Don'ts

### Do:
- **Do** show pending as its own line under the confirmed balance, labelled "not counted".
- **Do** set all money with tabular figures and dim the centavos.
- **Do** step container tones for depth instead of adding shadows.
- **Do** pair every status ring or tick with words.
- **Do** use Material Symbols from `composeResources/drawable/symbol_*.xml`.
- **Do** keep Fraunces for brand voice and Geist for everything functional.

### Don't:
- **Don't** colour expenses red or income green; use `accents.income` / `accents.expense`, signed with + and −.
- **Don't** use hex outside `ui/theme/Color.kt`, and don't use `Color.Gray` or `Color.White` from guide samples.
- **Don't** let a pending item move the headline balance or share its line.
- **Don't** introduce a second accent hue in Ube; the system has one light.
- **Don't** use Fraunces for numbers, labels, or buttons.
- **Don't** wrap an iOS-style control or custom switch/dialog in Material clothing.
