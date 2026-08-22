// ARCHITECT-OWNED — these tests define correct capture behaviour; they must pass, never be edited to pass.
package com.rajnikant.moneybrain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    // --- sender gate ---

    @Test fun `bob sender variants pass`() {
        assertTrue(SmsParser.isBankSender("VM-BOBSMS"))
        assertTrue(SmsParser.isBankSender("BOBTXN"))
        assertTrue(SmsParser.isBankSender("jd-bobsms-s"))
    }

    @Test fun `hdfc sender variants pass`() {
        assertTrue(SmsParser.isBankSender("AD-HDFCBK"))
        assertTrue(SmsParser.isBankSender("VM-HDFCBK-S"))
    }

    @Test fun `others are rejected`() {
        assertFalse(SmsParser.isBankSender("AX-ZOMATO"))
        assertFalse(SmsParser.isBankSender("+919812345678"))
        assertFalse(SmsParser.isBankSender("VM-ICICIB"))
    }

    // --- amount normalisation ---

    @Test fun `rupee prefixes normalise`() {
        assertEquals(123450L, SmsParser.inrToPaise("Rs.1,234.50"))
        assertEquals(45000L, SmsParser.inrToPaise("INR 450"))
        assertEquals(45050L, SmsParser.inrToPaise("Rs 450.5"))
        assertEquals(45000L, SmsParser.inrToPaise("₹450"))
    }

    @Test fun `garbage amount is null`() = assertNull(SmsParser.inrToPaise("Rs.45.0.0"))

    // --- template matching (synthetic template; real ones arrive in stage B) ---

    private val testTemplate = SmsTemplate(
        id = "test-debit",
        bank = "BOB",
        direction = "OUT",
        regex = Regex("""(?<amount>Rs\.[\d,]+(?:\.\d{1,2})?) debited from A/c (?<account>X*\d+) to (?<merchant>[^\s.]+)(?:\. Ref (?<ref>\d+))?"""),
    )

    @Test fun `matching template parses fields`() {
        val parsed = SmsParser.parse(
            "VM-BOBSMS",
            "Rs.450.00 debited from A/c XX1234 to swiggy@ybl. Ref 123456789012",
            listOf(testTemplate),
        )
        assertEquals(45000L, parsed?.amountPaise)
        assertEquals("OUT", parsed?.direction)
        assertEquals("swiggy@ybl", parsed?.merchant)
        assertEquals("XX1234", parsed?.accountHint)
        assertEquals("123456789012", parsed?.referenceNo)
        assertEquals("test-debit", parsed?.templateId)
    }

    @Test fun `non bank sender never parses`() =
        assertNull(SmsParser.parse("AX-ZOMATO", "Rs.450.00 debited from A/c XX1234 to x", listOf(testTemplate)))

    @Test fun `unmatched body is null`() =
        assertNull(SmsParser.parse("VM-BOBSMS", "Your OTP is 482910", listOf(testTemplate)))

    @Test fun `empty template list is null`() =
        assertNull(SmsParser.parse("VM-BOBSMS", "Rs.450.00 debited from A/c XX1234 to x", emptyList()))
}

class SmsMaskTest {

    @Test fun `long digit runs mask with length preserved`() =
        assertEquals("Ref XXXXXXXXXXXX done", SmsMask.mask("Ref 123456789012 done"))

    @Test fun `account digits mask`() =
        assertEquals("A/c XXXXXXXX", SmsMask.mask("A/c 98765432"))

    @Test fun `amounts survive`() =
        assertEquals("Rs.2500.00 debited", SmsMask.mask("Rs.2500.00 debited"))

    @Test fun `grouped amounts survive`() =
        assertEquals("INR 1,23,456.78 credited", SmsMask.mask("INR 1,23,456.78 credited"))

    @Test fun `dates and times pass untouched`() =
        assertEquals("on 21-08-26 at 14:05", SmsMask.mask("on 21-08-26 at 14:05"))

    @Test fun `mixed message masks only the sensitive parts`() =
        assertEquals(
            "Rs.450.00 debited from A/c XXXXXX on 21-08-26. UPI Ref XXXXXXXXXXXX.",
            SmsMask.mask("Rs.450.00 debited from A/c 987654 on 21-08-26. UPI Ref 123456789012."),
        )
}

class FingerprintTest {

    @Test fun `same reference means same fingerprint regardless of time`() {
        val a = Fingerprint.of(45000, "OUT", "XX1234", 1_000_000L, "AB123")
        val b = Fingerprint.of(45000, "OUT", "XX1234", 99_000_000L, "ab123 ")
        assertEquals(a, b)
    }

    @Test fun `different references differ`() =
        assertNotEquals(
            Fingerprint.of(45000, "OUT", null, 0, "REF1"),
            Fingerprint.of(45000, "OUT", null, 0, "REF2"),
        )

    @Test fun `no reference uses the time bucket`() {
        val base = 12_000_000L // a bucket boundary (multiple of 120s)
        val sameBucket = Fingerprint.of(45000, "OUT", "XX1234", base + 30_000, null)
        val alsoSame = Fingerprint.of(45000, "OUT", "XX1234", base + 60_000, null)
        val laterBucket = Fingerprint.of(45000, "OUT", "XX1234", base + 300_000, null)
        assertEquals(sameBucket, alsoSame)
        assertNotEquals(sameBucket, laterBucket)
    }

    @Test fun `direction and amount always separate`() {
        assertNotEquals(
            Fingerprint.of(45000, "OUT", null, 0, null),
            Fingerprint.of(45000, "IN", null, 0, null),
        )
        assertNotEquals(
            Fingerprint.of(45000, "OUT", null, 0, null),
            Fingerprint.of(45001, "OUT", null, 0, null),
        )
    }
}
