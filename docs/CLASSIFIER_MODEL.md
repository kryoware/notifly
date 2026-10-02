# Bundled notification classifier

Android capture runs `ModelNotificationParser` on the service's IO dispatcher.
It loads `shared/src/main/assets/notification_model.json` lazily once,
then classifies the installed app's display label plus notification title/text.
Package names are the fallback when Android cannot resolve a label. Both are
observed input; derived amounts, categories and transaction types are not features.

The asset is a 124,898-byte, format-version-2 export from the `notifly-model`
training project: character 3–5 gram TF-IDF and logistic regression with int8
weights. Kotlin performs inference using Android's built-in JSON support and
standard math. No network, downloaded model, GPU or extra ML runtime is needed.
The GPU is used only by the training project. Amount and account/reference digit
runs are normalized for classification; money is extracted from the original text
using exact `Long` minor units.

## Capture behavior

- Predictions at or above the bundled 0.8 threshold suggest income or expense.
  Confident `other` notifications are recorded as unrecognized without creating
  a transaction. An uncertain prediction uses the existing rules with low
  direction confidence; missing amounts still create no transaction.
- PHP, peso-sign, `P` amounts and explicit paid/received/sent GCash amounts are
  supported. Unsupported currencies, sub-cent amounts, overflow, failed/pending
  payments and inputs longer than 4,096 Unicode code points are unrecognized.
- Whole-word evidence is scoped to fewer than ten intervening words and at most
  two physical lines from the amount. `Holdings`, `withholding`, `unpaid` and
  `unreceived` do not match `hold`, `paid` or `received`. Training and inference
  use the same amount window. Transaction probability decreases with word/line
  distance; nearby balance, price/promotion and incomplete-payment notices are
  rejected. A distant email footer cannot establish an incoming payment.
- `PHP10000.00` remains 10,000 pesos. `400K` becomes 400,000, `20k` becomes
  20,000 and `1.25k` becomes 1,250 using exact minor-unit arithmetic. Bare compact
  amounts require nearby transaction evidence and assume PHP. `400KB`, `20km`,
  malformed amounts and overflow cannot be partially parsed into smaller amounts.
- Receipts/invoices suggest expenses with low direction confidence and require
  settlement review. Explicit incoming payment wording still suggests income.
  Several plausible amounts also lower amount confidence; a nearby balance is
  excluded before transaction amount selection.
- Transfer ownership cannot be established by the model. Existing finance-app
  counterparty and own-account hints still flag likely transfers for review;
  existing capture deduplication and transfer-leg pairing are retained.
- Captured transactions always have `NEEDS_REVIEW` status and do not change the
  confirmed balance. The user must check the account, amount and direction.
- App account estimates and the existing category editor are retained. This port
  does not add configurable account aliases or automatic custom category matching.
- The allow-list is checked before reading notification content. Any allow-listed
  app can be classified, including apps not marked as financial. Notification
  bodies stay local and follow the existing optional retention policy.

## Accuracy and updates

This is a prototype (`demo_only: true`), trained with synthetic examples and a
small manually verified sample. Its threshold is not a guarantee of accuracy;
held-out prototype scores do not establish performance on other apps or wording.
Ownership and custom categories still require user configuration or review.

To update, retrain/export in `notifly-model`, copy the JSON asset and port any
runtime format changes to `NotificationClassifier.kt`. Keep the Kotlin runtime
and shared `AmountContext.kt` helper in agreement with `classifier.py` and
`amount_context.py`; the Android tests include Python reference
probabilities, numeric invariance, filtering, rule fallback and a capture-to-Room
check that verifies review status, zero confirmed balance, privacy and deduplication.
`shared/src/androidUnitTest/resources/notifications.regression.json` replays the
same 62 regression cases maintained in the training project, including the two
reported screenshots. Related training templates exist; passing these checks
demonstrates regression behavior, not accuracy on unseen notifications.

Run `./gradlew :shared:testDebugUnitTest :app:assembleDebug` after an update.
`allTests` also includes the declared iOS targets; existing iOS compilation errors
are independent of this Android-only classifier.

