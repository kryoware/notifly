package ph.notifly.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test fun exactCentavosAndValidation() {
        assertEquals(12345L, parseAmountMinor("123.45"))
        assertEquals(10L, parseAmountMinor("0.1"))
        assertEquals(Long.MAX_VALUE, parseAmountMinor("92233720368547758.07"))
        listOf("", "0", "-1", "1.001", "NaN", "1e3", "92233720368547758.08").forEach { assertNull(parseAmountMinor(it)) }
        assertEquals("-92233720368547758.08", amountText(Long.MIN_VALUE))
    }
}
