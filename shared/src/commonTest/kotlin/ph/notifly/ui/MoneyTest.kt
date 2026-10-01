package ph.notifly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun signedMoneyHandlesTheFullLongRange() {
        assertEquals("+₱1,234,567.89", signedMoney(123456789L))
        assertEquals("−₱1,234,567.89", signedMoney(-123456789L))
        assertEquals("₱0.00", signedMoney(0L))
        assertEquals("+₱92,233,720,368,547,758.07", signedMoney(Long.MAX_VALUE))
        assertEquals("−₱92,233,720,368,547,758.08", signedMoney(Long.MIN_VALUE))
    }

    @Test fun exactCentavosAndValidation() {
        assertEquals(12345L, parseAmountMinor("123.45"))
        assertEquals(10L, parseAmountMinor("0.1"))
        assertEquals(Long.MAX_VALUE, parseAmountMinor("92233720368547758.07"))
        listOf("", "0", "-1", "1.001", "NaN", "1e3", "92233720368547758.08").forEach { assertNull(parseAmountMinor(it)) }
        assertEquals("-92233720368547758.08", amountText(Long.MIN_VALUE))
    }
    @Test fun moneyGroupsThousandsAndAmountTextRoundTrips() {
        assertEquals("₱1,234,567.89", money(123456789L))
        assertEquals("−₱1,234,567.89", money(-123456789L))
        assertEquals("₱0.00", money(0L))
        listOf(5L, 12345L, 123456789L, Long.MAX_VALUE).forEach {
            assertEquals(it, parseAmountMinor(amountText(it)))
        }
    }
}
