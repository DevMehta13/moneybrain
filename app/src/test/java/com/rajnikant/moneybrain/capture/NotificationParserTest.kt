// ARCHITECT-OWNED — these tests define notification-capture behaviour; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    @Test fun `payment apps are gated by exact package`() {
        assertTrue(NotificationParser.isPaymentApp("com.google.android.apps.nbu.paisa.user"))
        assertTrue(NotificationParser.isPaymentApp("com.bankofbaroda.mconnect"))
        assertFalse(NotificationParser.isPaymentApp("com.whatsapp"))
        assertFalse(NotificationParser.isPaymentApp("com.google.android.apps.nbu.paisa.user.fake"))
    }

    @Test fun `non payment app never parses`() =
        assertNull(NotificationParser.parse("com.whatsapp", "₹500", "paid"))

    @Test fun `empty template list parses nothing`() =
        assertNull(
            NotificationParser.parse(
                "com.google.android.apps.nbu.paisa.user",
                "Payment successful",
                "You paid ₹500 to Sharma General Store",
            ),
        )

    // Synthetic template proves the matching machinery; real templates arrive in stage B.
    private val testTemplate = NotificationTemplate(
        id = "test-gpay-paid",
        packageName = "com.google.android.apps.nbu.paisa.user",
        direction = "OUT",
        regex = Regex("""(?i)You paid (?<amount>₹[\d,]+(?:\.\d{1,2})?) to (?<merchant>[^\r\n]+)"""),
    )

    @Test fun `matching template parses amount and merchant`() {
        val parsed = NotificationParser.parse(
            "com.google.android.apps.nbu.paisa.user",
            "Payment successful",
            "You paid ₹1,234.50 to Sharma General Store",
            listOf(testTemplate),
        )
        assertEquals(123_450L, parsed?.amountPaise)
        assertEquals("OUT", parsed?.direction)
        assertEquals("Sharma General Store", parsed?.merchant)
    }

    @Test fun `template bound to another package does not match`() =
        assertNull(
            NotificationParser.parse(
                "com.bankofbaroda.mconnect",
                "Payment successful",
                "You paid ₹1,234.50 to Sharma General Store",
                listOf(testTemplate),
            ),
        )
}
