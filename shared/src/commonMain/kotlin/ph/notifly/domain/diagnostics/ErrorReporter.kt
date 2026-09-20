package ph.notifly.domain.diagnostics

enum class ErrorSite { NOTIFICATION_CAPTURE, CAPTURE_MAINTENANCE, SCREEN_MODEL, UNDO, INSTALLED_APPS }

interface ErrorReporter {
    fun report(throwable: Throwable, site: ErrorSite)
    object None : ErrorReporter {
        override fun report(throwable: Throwable, site: ErrorSite) = Unit
    }
}
