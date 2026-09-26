package ph.notifly.ui

/** CSV sharing is unavailable on iOS; this call has no effect. */
actual fun shareCsv(csv: String, filename: String) = Unit
