# Screen generation prompts

Tool: built-in imagegen. Generated 2026-09-26. One dedicated generation per screen; no screen crops.

## Reference images

- First reference for every original generation: [Quiet Fold brandkit](../brandkit.png).
- Second reference for screens 02–09: [Home](01-home.png), fixing device proportions and visual consistency.
- Final refinements used each respective first-generation screen as the sole edit target.

## Shared prompt

For each original generation, concatenate this shared prompt with the corresponding screen brief below. Screen 01 used the shared prompt without the final CONSISTENCY DETAIL paragraph and only the brandkit reference.

Use case: ui-mockup. Generate ONE high-fidelity full mobile app screen image for Notifly, Android personal finance tracker. This is a screen design deliverable, not code. Portrait canvas around 1024x2048. A single straight-on modern Android phone, slim matte ink frame, centered punch-hole camera, small 9:41 status bar, native bottom gesture bar. Full phone completely visible, narrow even outer margins on neutral warm gray. Phone fills almost all canvas. No angled perspective, no desk props, no labels outside phone, no multi-screen collage. Large, crisp, comfortably readable typography.

REFERENCE ROLES: Image 1 is the approved Quiet Fold brandkit, the authoritative visual identity. If a second image is supplied, it is the established Home screen and is authoritative for the exact device, palette, typography scale, spacing and component language. Create a NEW screen with its specified content, do not reproduce the brand board or clone the Home layout.

LOCKED DESIGN SYSTEM: Same original folded n mark and lowercase notifly wordmark as brandkit. Warm rice-paper ivory background; near-black aubergine ink text; persimmon primary actions; dusty clay secondary surfaces and fine ledger rules. No additional bright colors. Subtle paper tactility only in brand art, almost imperceptible texture in content backgrounds. Extremely legible high-contrast financial UI. Humane editorial serif page titles 30-34dp, clean geometric sans body 16dp, labels at least 13dp, tabular monetary numerals, 48dp touch targets, 24dp horizontal padding, 8dp spacing rhythm. 14-16dp radii for sheets or purposeful callouts, flat list rows separated by fine rules. Avoid nested cards, gradient charts, glossy effects, neon and excessive decorative pills. Material Symbols Rounded icons throughout, consistent weight, no bespoke replacement UI icons. Fold motif as a tiny brand detail only. Primary buttons persimmon with dark ink labels for strong contrast. Serious but warm consumer finance app, not a bank or payment wallet.

NAVIGATION: Four destinations in exact order Home, Transactions, Insights, Settings. Bottom navigation shows the four short labels and Material Symbols home, list, pie_chart, settings; active item ink icon on pale clay pill with small persimmon cue. Main destinations retain this identical bar. Focused review/editor/setup/drill-down screens use a back or close app bar and a clear bottom action; no duplicate navigation. Android safe areas and comfortably large text throughout.

PRODUCT TRUTHS: Raw notification text stays on device. Capture only apps the owner explicitly allows. Parsed transactions always need human review and never automatically confirm. Pending values never affect headline balance, totals or insights. Notification access opens Android settings, not a runtime permission dialog. Sync can send only confirmed transactions, never notification bodies. No invented banking, investment or transfer capability. All displayed names, amounts and counts are fictional concept data. Never depict a raw message body or real private data.

VISUAL QUALITY: Polished, original, implementable product design faithfully extending the reference brandkit. Calm whitespace, strong hierarchy, balanced density, beautiful typography, no fake microcopy or tiny decorative text. Every specified word must be spelled correctly, controls must fit, no content overlaps. The following screen-specific brief takes priority for content and arrangement.

CONSISTENCY DETAIL: Match the second reference's device frame, typography and roomy ledger layout. Keep button fills flat (no gradient), and use dark ink labels on bright persimmon buttons so text contrast is strong. Slight rounded serif titles and full-width rows; do not amplify decorative grain.

## 01-home — Home

HOME, active Home tab. Top app bar original small folded mark + notifly, right small segmented sync ring with cloud-off symbol. Compact readable status below it "Offline · 3 to sync"; only confirmed items queue for sync.
Editorial heading "Your money, in view."
Large unboxed metric "Confirmed balance" then "₱24,680.00" in 40dp clean tabular type. Tiny supporting label "Confirmed transactions only".
A warm clay review strip separated from the metric: "2 need your review", amount "₱860.00", short line "Not included in your balance", trailing clearly readable "Review" action. Make this distinction unmistakable.
Section heading "Recent" and right text action "See all". Three flat rows with Material Symbols category avatars:
"Groceries" / "Food · GCash" / "−₱1,250.00"
"Salary" / "Income · BPI" / "+₱28,000.00"
"Coffee" / "Food · Maya" / "−₱160.00"
All three recent entries confirmed, no unconfirmed amount included.
Lower right extended persimmon FAB plus symbol and "Add transaction" above bottom nav. Whitespace around list and metric; no pointless chart.

## 02-transactions — Transactions

TRANSACTIONS, active Transactions tab. Title "Transactions", Material search and overflow symbols. A single clean segmented row "All" (selected), "Needs review", "Confirmed".
Date group "Today". Two pending flat rows:
"Coffee" / "Maya · Needs review" / "−₱180.00"
"Groceries" / "GCash · Needs review" / "−₱680.00"
Each has a subtle small persimmon pending marker plus explicit status, not a confirmed check.
Date group "Yesterday". Three confirmed rows:
"Groceries" / "GCash · Confirmed" / "−₱1,250.00"
"Salary" / "BPI · Confirmed" / "+₱28,000.00"
"Coffee" / "Maya · Confirmed" / "−₱160.00"
No more rows than fit comfortably. Fine ledger separators, readable right-aligned amounts, neutral tonal Material category avatars. Helpful quiet footer "Long-press to select". Persimmon floating plus button above bottom bar. App should read like a refined personal ledger, never a banking marketing dashboard.

## 03-review — Review transaction

FOCUSED REVIEW TRANSACTION, no bottom destination tabs. Top bar back arrow, title "Review transaction", overflow. Pale clay status "Needs review".
Large merchant "Coffee", large outgoing amount "−₱180.00".
One short explanatory line "Check the details before this counts."
Flat editable detail rows with subtle pencil/chevron affordances: "Type" = "Expense", "Category" = "Food & drink", "Source" = "Maya", "Date" = "26 Sep 2026", "Time" = "9:12 AM".
A spare folded-paper motif near the source summary, not distracting. Quiet privacy statement "Parsed on your phone." Then "Not included in your balance yet."
Two bottom actions: full-width persimmon primary button "Confirm transaction"; secondary text action "Edit details". A separate lower-emphasis "Delete" text action in ink with trash symbol, sufficiently apart from confirmation. This is before confirmation, never show success or auto-confirm.
No raw notification preview, no automatic confirmation toggle, no amount changes.

## 04-add-transaction — Add transaction

MANUAL ADD TRANSACTION form, no bottom tabs. Close X app bar and title "Add transaction". One restrained segmented control "Expense" (selected), "Income", "Transfer".
Prominent outlined or underline monetary input label "Amount (PHP)", typed value "₱320.00".
Large comfortable form fields: "Description" with "Lunch"; "Category" with "Food & drink" and dropdown arrow; date "26 Sep 2026" and time "12:30 PM" in one well-spaced row; source field labeled "Source" value "Manual". Use clean native Material outlined inputs and a minimal pencil icon only where needed.
Optional single-line "Note" field empty with placeholder "Add a note".
Footer helper "You’re adding this transaction yourself."
Bottom full-width persimmon button "Save transaction" plus quiet "Cancel" text action. No keyboard showing; fields and buttons fit in full view. A practical beautiful form, no large decorative art, no invented account number. Saving an explicitly entered manual transaction is user confirmation.

## 05-insights — Insights

INSIGHTS, active Insights tab. App bar title "Insights", compact month selector "September 2026" with chevrons.
Small label "Confirmed spending", prominent "₱8,320.00". Quiet subline "Needs-review items are excluded".
One purposeful, elegant horizontal spending-by-category visualization with exact labels and aligned amounts; no random line chart:
"Food & drink" = "₱3,120.00" (longest persimmon bar, about 38% of total)
"Shopping" = "₱2,600.00" (second bar, ink)
"Transport" = "₱1,600.00" (third bar, darker clay)
"Other" = "₱1,000.00" (shortest bar, muted clay)
These sum exactly to ₱8,320.00. Bars all start at same left edge and have correct relative lengths. Pair each category with dark readable text, no reliance on color alone.
Lower compact unboxed summary "Confirmed income" with "₱33,000.00"; another row "Net this month" with "+₱24,680.00".
Quiet small comparison line "Your largest category is Food & drink." Spacious useful chart, not a dashboard full of cards. No money forecasts, investment returns, bank payment action or chart decoration.

## 06-settings — Settings

SETTINGS, active Settings tab. Editorial title "Settings", small original brand mark. Flat grouped native preference rows, fine ledger separators, no boxes inside boxes.
Group "Capture":
"Notification access" trailing "Connected" plus chevron.
"Allowed apps" trailing "3 apps" plus chevron.
"Notification log" trailing chevron.
Group "Privacy & sync":
"Sync" trailing "Offline · 3 queued" with small partial sync ring.
Small helper below this row "Only confirmed transactions sync."
"App lock" trailing "On" plus chevron.
Group "Appearance":
"Theme" trailing "Light" plus chevron.
"Palette" trailing "The Quiet Fold" with tiny persimmon swatch and chevron.
Generous bottom breathing space with small "notifly" wordmark. Keep all rows readable and at least 48dp high. No fake profile identity, no needless avatar, no subscription upsell.

## 07-allowed-apps — Allowed apps

ALLOWED APPS setup/drill-down screen, back arrow and title "Allowed apps", no bottom tabs.
Editorial helper "Only the apps you choose."
Supporting copy "Notifly looks for transactions in notifications from these apps."
Comfortable full-width search field "Search apps", Material search icon.
Small section label "3 apps allowed".
Flat installed-app rows with neutral monogram avatars, native toggles at right:
"GCash" / "12 captures" / ON
"Maya" / "8 captures" / ON
"BPI" / "4 captures" / ON
"Messages" / "Not allowed" / OFF
"Messenger" / "Not allowed" / OFF
Switches selected in dark persimmon/ink, unselected clay. Make enabled and disabled unmistakable by switch knob positions and ON/OFF visual contrast, not text alone. Do not copy third-party brand logos; neutral initials are sufficient.
Bottom quiet line "Your choices stay on this device." Sticky persimmon "Done" button. Do not show every app preselected. No notification content displayed.

## 08-notification-log — Notification log

NOTIFICATION LOG, back arrow and title "Notification log", clear-log trash icon on right, no bottom tabs.
Quiet informational line "Capture activity on this device."
Filter segmented row "All" selected, "Parsed", "Unrecognized".
Flat chronological audit rows with source app and result, never raw message bodies:
"Maya" / "Parsed · Needs review" / "9:12 AM"
"GCash" / "Parsed · Needs review" / "8:46 AM"
"BPI" / "Unrecognized" / "8:10 AM"
Expand the BPI row only with a calm indented detail block "No amount found." and "No transaction created." plus clearly readable outlined button "Add manually".
A fourth compact row "Maya" / "Duplicate skipped" / "8:02 AM".
Lower area privacy setting row "Keep raw text on device" native toggle OFF. Small readable helper "Off. Only capture metadata is kept." This is a design concept; no actual raw text appears anywhere.
Paper-toned flat surface, fine separators, all result states clear through text and distinct Material symbols. No fake transaction or invented amount in the unrecognized branch.

## 09-notification-access — Notification access

NOTIFICATION ACCESS entry/setup screen, back arrow in app bar, no bottom navigation.
Small original notifly wordmark. Upper middle one thoughtfully art-directed sculptural persimmon paper fold from the brandkit, warm tactile ivory backdrop, contained and taking only about 20% of screen height.
Large readable editorial headline in 2 lines "Caught quietly." / "Counted by you."
Short explanation "Allow notification access in Android settings."
Two concise illustrated rows using Material symbols:
"Choose which apps to capture."
"Review every transaction first."
Additional concise trust line "Notification text stays on your phone."
A separated status row "Notification access" and "Not enabled".
Bottom prominent persimmon button "Open Android settings", below quiet secondary button "Continue manually".
This is an in-app explanation leading to Android special-access settings; do not depict an OS runtime permission popup, do not label button simply Allow, do not imply permission was granted, do not auto-advance. Premium composed onboarding style with strong brand presence and clear real actions.

## Final refinements

### 08-notification-log

Edit this one Notifly notification-log screen image. Preserve the entire device framing, page layout, every word, every amount/count/time, controls, colors and typography except these specific status icons:
1. On the Maya 9:12 AM row and GCash 8:46 AM row, replace the green success check circle with a small persimmon clock/schedule icon indicating PENDING REVIEW. No checkmark and no green anywhere. Keep the exact text "Parsed · Needs review" next to each clock.
2. Replace the amber-orange exclamation circle beside "Unrecognized" with an ink-colored outlined exclamation circle. Use only the existing ink, paper, persimmon and clay palette.
This change is semantic: parsing is not confirmation. These entries remain awaiting review. Do not change the off toggle, unrecognized details, words or any other part of the screen.

### 09-notification-access

Edit this one Notifly notification-access screen image. Preserve the entire image, device, fold illustration, layout, spacing, typography, icons, buttons and all other copy. Change ONLY the small supporting line directly beneath "Notification text stays on your phone." Replace the inaccurate line "We never send or store your raw notifications." with the EXACT text "Raw notification text never leaves this device." Keep it fully readable at the same size, wrap naturally to two lines only if necessary. The app can optionally retain notification text on the device, so never claim it cannot store locally. Do not add or change anything else.

