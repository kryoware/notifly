package ph.notifly.domain.model

data class AllowedApp(
    val packageName: String,
    val label: String,
    val kind: String,
    val listening: Boolean,
    val capturedCount: Int = 0,
    val finance: Boolean = false,
)
