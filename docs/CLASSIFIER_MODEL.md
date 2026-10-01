# Bundled notification classifier

Android capture runs `ModelNotificationParser` on the service's IO dispatcher.
It loads `shared/src/main/assets/notification_model.json` lazily once,
then classifies the installed app's display label plus notification title/text.
Package names are the fallback when Android cannot resolve a label. Both are
observed input; derived amounts, categories and transaction types are not features.

The asset is a 124,482-byte, format-version-1 export from the `notifly-model`
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
in agreement with `classifier.py`; the Android tests include Python reference
probabilities, numeric invariance, filtering, rule fallback and a capture-to-Room
check that verifies review status, zero confirmed balance, privacy and deduplication.

Run `./gradlew :shared:testDebugUnitTest :app:assembleDebug` after an update.
`allTests` also includes the declared iOS targets; existing iOS compilation errors
are independent of this Android-only classifier.

