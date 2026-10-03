package ph.notifly.ui

import kotlin.test.*

class NotificationExportTest {
    @Test fun releaseRejectsExport() {
        assertFailsWith<IllegalStateException> { requireNotificationExport(debuggable = false) }
    }

    @Test fun debugAllowsExport() {
        requireNotificationExport(debuggable = true)
    }
}
