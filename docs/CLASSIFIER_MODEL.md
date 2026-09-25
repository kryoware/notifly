Running a local model for financial notification classification is the perfect use case for on-device machine learning. Because you are handling highly sensitive financial data, processing it locally ensures absolute privacy, zero server costs, and instant offline processing.

For a simple categorization task (Incoming, Outgoing, Balance Transfer, Ignore), **you do not need a generative Large Language Model (LLM)** like Llama or Gemma. Running an LLM on every notification will drain the user's battery and consume too much RAM. Instead, you need a **Mobile Text Classifier**.

Here is the architectural blueprint and workflow to build and deploy this in your Android app.

## 1. Choosing the Right Model Architecture

Since notification text is short (usually under 200 characters), you want a model optimized for sequence classification.

* **MobileBERT or DistilBERT (Recommended):** These are compressed versions of BERT. They understand the semantic context of language (e.g., distinguishing between "You paid John $50" and "John paid you $50"). Once quantized (converted to INT8), they shrink to about 15–30 MB.
* **Average Word Embedding / FastText (Ultra-light):** If you are on a strict app size budget, a simple neural network that averages word embeddings can weigh as little as 1–5 MB. It is slightly less accurate than BERT but uses virtually zero battery.

## 2. The Training Pipeline

To get the model to understand your specific categories, you need to fine-tune it.

1. **Gather Training Data:** Create a CSV dataset of dummy notifications mimicking banks, Uber, DoorDash, and Amazon.
* *Text:* "Uber trip canceled. $15.00 refunded to your card ending in 1234." -> *Label:* `incoming`
* *Text:* "You sent $20 to Alice for dinner." -> *Label:* `outgoing`
* *Text:* "Transferred $500.00 from Savings to Checking." -> *Label:* `balance_transfer`
* *Text:* "Your Dasher is approaching." -> *Label:* `ignore`


2. **Fine-Tune:** Use **Hugging Face AutoTrain** (no-code) or **TensorFlow Lite Model Maker** (Python). You can fine-tune a pre-trained DistilBERT model on your CSV dataset in less than an hour on a standard Colab GPU.
3. **Quantize and Export:** Convert the trained model into a `.tflite` (TensorFlow Lite) file. Apply INT8 quantization during export to reduce the file size by 4x with almost no loss in accuracy.

## 3. Android Implementation

Google's **MediaPipe Tasks API** (the successor to TFLite Task Library) is the most modern and efficient way to run this on Android.

**Step 1: Add MediaPipe Dependencies**
Add this to your `build.gradle`:

```gradle
dependencies {
    implementation 'com.google.mediapipe:tasks-text:0.10.0'
}

```

**Step 2: Place the Model**
Put your `.tflite` model inside your Android project's `src/main/assets` folder.

**Step 3: Run the Inference**
Initialize the classifier and run it whenever a notification arrives:

```kotlin
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier

// 1. Initialize the classifier (Do this once, e.g., in a Singleton or ViewModel)
val baseOptions = BaseOptions.builder().setModelAssetPath("finance_classifier.tflite").build()
val options = TextClassifier.TextClassifierOptions.builder()
    .setBaseOptions(baseOptions)
    .build()
val classifier = TextClassifier.createFromOptions(context, options)

// 2. Classify the notification text
val notificationText = "Payment of $45.00 to Doordash was successful."
val results = classifier.classify(notificationText)

// 3. Extract the highest probability category
val bestCategory = results.classificationResult().classifications().first().categories().maxByOrNull { it.score() }
println("Category: ${bestCategory?.categoryName()}, Confidence: ${bestCategory?.score()}")

```

## 4. Android System Integration (Notification Listener)

To actually intercept the notifications, your app will require the `BIND_NOTIFICATION_LISTENER_SERVICE` permission.

1. Create a service extending `NotificationListenerService`.
2. Override `onNotificationPosted`.
3. Extract the `android.title` and `android.text` from the notification extras.
4. Filter by package names first (e.g., only run the ML model if the package is `com.venmo`, `com.chase`, `com.uber`). This saves battery by not running the neural network on WhatsApp messages or system alerts.
5. Pass the text to your MediaPipe TextClassifier.

---

This output perfectly illustrates why you are making the right choice to move to a Machine Learning model.

Based on the `reason` column ("Found an amount but could not tell..."), your current rudimentary classifier is relying on **rules or regular expressions (Regex)**. It is successfully spotting the Philippine Peso symbol (₱) or "P", but it is failing because these aren't actually ledger transactions.

Here is what your data is revealing and how the ML approach will effortlessly fix it.

## The Problem: The "Marketing Spam" Trap

All three of these notifications are **promotions, upsells, or marketing bait**.

* **ShopBack:** Uses "₱1000 bonus" to lure the user into making orders. No money has moved yet.
* **GOMO PH:** Uses "₱199" as a price advertisement for a data promo. No purchase has actually occurred.
* **PalawanPay:** A cashback advertisement. While it says "earned P19.55", the second half of the sentence ("Keep using... to earn up to ₱1,000") heavily skews it into marketing material.

A rule-based classifier gets confused here because it sees money but cannot find clear directional verbs like "paid," "sent," or "transferred to."

## The Solution: Categorize First, Extract Second

To build a robust financial scraper, you need to split your logic into two distinct steps. Do not try to find the direction and the amount at the same time.

### Step 1: Intent Classification (The ML Model)

You will use the MobileBERT model we discussed earlier to simply ask: **"Is this a real transaction?"**

You should take the exact strings from your CSV and add them to your Colab training data under the label `ignore` (or a dedicated `promo` label):

```python
    # Add to your training dataset
    ("Sunday's almost over 🌙 — ₱1000 bonus for 5 orders sa Adidas, no min spend + 20% Cashback.", "ignore"),
    ("15GB for just ₱199 and NEVER expires? Yes, really.", "ignore"),
    ("You've earned P19.55 cashback from your Visa Card spend! Keep using your PalawanPay...", "ignore"),

```

Because MobileBERT understands language context, it will learn that words like *bonus, promo, no min spend, just, never expires,* and *habol na* mean the notification is an advertisement, regardless of whether a ₱ symbol is present.

### Step 2: Amount Extraction (Regex)

Once the ML model successfully filters out the marketing spam, you can confidently run a Regex script to extract the amount, knowing that the notification is a guaranteed transaction.

Your Android logic will look like this:

```kotlin
// 1. Pass the raw_body to the ML model
val category = classifier.classify(notificationText)

// 2. Only extract amounts if it's a real transaction
if (category == "incoming" || category == "outgoing" || category == "balance_transfer") {
    
    // Now you can safely use your regex, because you KNOW it's a real transaction
    val amountRegex = Regex("(?i)(?:₱|PHP|P)\\s*([\\d,]+\\.?\\d{0,2})")
    val match = amountRegex.find(notificationText)
    
    if (match != null) {
        val amount = match.groupValues[1]
        saveToDatabase(category, amount)
    }
} else {
    // If category is "ignore", silently drop the notification
    Log.d("Classifier", "Marketing spam ignored.")
}

```

By adding 50 to 100 of these promotional notifications to your training dataset under the `ignore` category, your ML model will quickly learn to act as a highly accurate spam filter, leaving your rule-based extractor to do what it does best: pull numbers out of clean data.

---

