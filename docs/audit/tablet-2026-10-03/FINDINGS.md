# Tablet audit: HUAWEI AGRK-W09 (Android 10, ~601x961dp)

Screenshots live next to this file, named `NN-screen-orientation[-theme][-fs130].png`.
Score: **11/20 (Acceptable)**. Accessibility 3, Performance 3, Theming 2, Platform conformance 2, Adaptivity 1.

## P1
1. **Launch route replays on every recreation.** `app/.../MainActivity.kt:70` re-reads `EXTRA_ROUTE`; `NotiflyApp.kt:196` navigates again. Seen in `41-*`. Fix: only read it when `savedInstanceState == null`.
2. **Transient UI state lost on rotation** (`remember` instead of `rememberSaveable`): selection `Screens.kt:456`, bulk delete `Screens.kt:457`, discard dialog `NotiflyApp.kt:257`, pay-bill sheet `BillsScreen.kt:85`, balance dialog `Screens.kt:186`, delete dialogs `Screens.kt:707`, `BillsScreen.kt:400`. Seen in `13-*`, `21-*`, `24`/`25`, `42-*`.
3. **Status-bar icons invisible with in-app Dark theme.** `MainActivity.kt:68` `enableEdgeToEdge()` follows the system theme. Seen in `50-*-dark`.
4. **Content stretches to full width everywhere.** Seen in `11`, `17`, `20`, `22`, `23`, `01`-`04`, `39`. Fix: cap and centre content (~600dp forms, ~840dp lists), starting with `AppDestination` (`NotiflyApp.kt:234`).
5. **No list-detail on wide screens.** Transactions -> editor, Bills -> bill, Log -> capture, Settings -> sub-screens. Needs `material3-adaptive` layout/navigation deps (only `navigation-suite` today).

## P2
6. Landscape + IME leaves one field above the pinned Save bar (`20-editor-add-landscape`, `12-*`).
7. Home ignores width; FAB covers account tiles (`10-home-landscape`). Two panes.
8. Bills tabs stretch; calendar day detail below the fold (`14`, `15`). Side-by-side on wide screens.
9. FAB covers the last row's confirm ring and stays during selection (`11`, `13`).
10. Secondary screens drop the rail (`NotiflyApp.kt:110-114`, `30`-`42`).
11. Insights is a single column of cards (`16`).
12. Onboarding step 4: duplicate header, 21px vs 32px margins (`04`).
13. Demo data reshuffles on recreation (`NotiflyApp.kt:63,65`).

## P3
14. Font scale 1.3: subtitles wrap, amount column ragged (`56-*-fs130`).
15. Search cursor jumps to start after rotation (`12-portrait`).
16. Bill editor inline Save vs pinned Save; segmented widths differ (`23` vs `20`).
17. Demo inconsistencies: empty Accounts vs Home wallets (`33`); "Lazada refund" avatar/source mismatch.

## Working
Rail switches correctly; sheets are width-capped (`24`); lock screen is constrained (`44`); editor text and nav history survive rotation; colours come from theme roles.
