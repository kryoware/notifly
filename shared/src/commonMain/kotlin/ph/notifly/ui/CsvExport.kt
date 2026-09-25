package ph.notifly.ui

/** Requests platform sharing for [csv] under [filename]; unsupported platforms may do nothing. */
expect fun shareCsv(csv: String, filename: String)
