package ph.notifly.ui

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ph.notifly.domain.model.TransactionStatus
import ph.notifly.domain.model.TransactionType

class DemoRowsTest {
    @Test fun everySeedShowsReviewIncomeAndUniqueIds() {
        repeat(50) { seed ->
            val rows = demoRows(Random(seed))
            assertEquals(rows.size, rows.map { it.id }.toSet().size)
            assertTrue(rows.all { it.amountMinor > 0 })
            assertTrue(rows.any { it.status == TransactionStatus.NEEDS_REVIEW })
            assertTrue(rows.any { it.type == TransactionType.INCOME && it.status == TransactionStatus.CONFIRMED })
            assertTrue(rows.any { it.type == TransactionType.TRANSFER })
        }
    }
}
