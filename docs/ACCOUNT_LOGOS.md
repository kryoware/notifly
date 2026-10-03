# Bank and card artwork

Checked 2026-10-03 for a commercial personal-finance app that identifies saved
accounts and does not accept or process payments.

No third-party bank/card logo is bundled: public access to artwork is not a
verified license for this use. Account pickers display the name, card network,
last four digits, and Apache-2.0 Material Symbols for bank/card/wallet types.

| Source | Verified terms | Decision |
| --- | --- | --- |
| [Brandfetch terms, §5.1](https://brandfetch.com/terms) | The service/content license explicitly excludes original third-party logos. Offline caching also requires applicable terms and refreshes. | A discovery source; not a commercial logo license. |
| [Logo.dev terms, Third Party IP](https://www.logo.dev/legal/terms) | Access to third-party IP does not grant a license to it. | A discovery source; not a commercial logo license. |
| [Visa media kit](https://corporate.visa.com/en/about-visa/mediakits.html) | Logo and symbol are for media usage only. | Does not establish permission for an account picker. |
| [Visa brand guidelines](https://merchantsignage.visa.com/brandguidelines) / [Mastercard Brand Center](https://www.mastercard.com/brandcenter/us/en/home.html) | Payment/acceptance branding has specific usage rules. | Do not assume a payment tracker is a licensed payment partner. |

## Sourcing route

Obtain permission from each bank/card network's brand or licensing contact for
display alongside user-saved accounts in a commercial Android/iOS app. Request
offline redistribution in the app bundle and theme-compatible monochrome
artwork (the app's colors must come from MaterialTheme). Ask for required
attribution, clear space, minimum size, territory, and expiry/revocation terms.

For each approved asset, commit the original source URL, grant or applicable
public terms, permitted use, required attribution, and approval/expiry dates
alongside the asset. Then replace the matching fallback in `AccountSymbol.kt`
with that bundled Compose drawable. Match a verified institution identifier or
card network; do not infer a bank from arbitrary account-name substrings.
Keep the fallback for brands without permission. Fetch artwork at development
time only; notification text and account identifiers must never be sent to a
logo service.

There is no logo subscription, API key, or runtime logo request in this change.
