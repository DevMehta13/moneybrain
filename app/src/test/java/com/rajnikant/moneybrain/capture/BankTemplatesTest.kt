// ARCHITECT-OWNED — these tests pin the templates to the real bank formats; never edit them to pass.
// Bodies reconstruct Rajnikant's actual masked samples (2026-08-22) with fake digits.
package com.rajnikant.moneybrain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankTemplatesTest {

    // --- HDFC UPI debit (multi-line) ---

    private val hdfcSent = """
        Sent Rs.255.00
        From HDFC Bank A/C *4321
        To DAS KHAMAN
        On 22/08/26
        Ref 527399104458
        Not You?
        Call 18002586161/SMS BLOCK UPI to 7308080808
    """.trimIndent()

    @Test fun `hdfc sent parses`() {
        val p = SmsParser.parse("JD-HDFCBK-S", hdfcSent)
        assertEquals(25_500L, p?.amountPaise)
        assertEquals("OUT", p?.direction)
        assertEquals("DAS KHAMAN", p?.merchant)
        assertEquals("4321", p?.accountHint)
        assertEquals("527399104458", p?.referenceNo)
        assertEquals("hdfc-upi-sent-1", p?.templateId)
    }

    @Test fun `hdfc multiword merchant parses`() {
        val body = hdfcSent.replace("DAS KHAMAN", "GUJARAT STATE ROAD TRANSP")
        assertEquals("GUJARAT STATE ROAD TRANSP", SmsParser.parse("VM-HDFCBK-T", body)?.merchant)
    }

    @Test fun `hdfc vpa merchant parses`() {
        val body = hdfcSent.replace("DAS KHAMAN", "9876501234@ptyes")
        assertEquals("9876501234@ptyes", SmsParser.parse("VD-HDFCBK-T", body)?.merchant)
    }

    @Test fun `hdfc mandate execution parses as a debit`() {
        val body = """
            UPI Mandate:
            Sent Rs.1950.00
            from HDFC Bank A/c 4321
            To Google Play
            15/08/26
            Ref 520011223344
            Not You? Call 18002586161/SMS BLOCK UPI to 7308080808
        """.trimIndent()
        val p = SmsParser.parse("JD-HDFCBK-S", body)
        assertEquals(195_000L, p?.amountPaise)
        assertEquals("OUT", p?.direction)
        assertEquals("Google Play", p?.merchant)
        assertEquals("520011223344", p?.referenceNo)
    }

    // --- HDFC messages that move no money must NOT parse ---

    @Test fun `hdfc mandate set does not parse`() {
        val body = """
            Mandate Set
            Rs.1950.00
            For Google Play
            From HDFC Bank A/c x4321
            UMN: 58fefaeb7e93e463e1234bcee10a20bd@ok
            Not you?
            Call 18002586161
        """.trimIndent()
        assertNull(SmsParser.parse("JX-HDFCBK-S", body))
    }

    @Test fun `hdfc mandate cancelled does not parse`() {
        val body = """
            Update:
            Rs. 1950.00 UPI mandate to Google Play has been cancelled from HDFC Bank A/c x4321.
            UMN: 58fefaeb7e93e463e1234bcee10a20bd@okhdfcb
        """.trimIndent()
        assertNull(SmsParser.parse("VM-HDFCBK-S", body))
    }

    @Test fun `hdfc promo from bank sender does not parse`() {
        val body = "Good news!\nYou can now receive up to 50 UPI payments in 24 hrs in your " +
            "HDFC Bank account. Limits apply.\nKnow more: https://1.hdfc.bank.in/HDFCBK/a/mlF4"
        assertNull(SmsParser.parse("AX-HDFCBK-S", body))
    }

    // --- Bank of Baroda UPI debit (single line) ---

    private val bobDebit = "Rs.60.00 Dr. from A/C 1234567890 and Cr. to crazzyproduct@axl. " +
        "Ref:858963521470. AvlBal:Rs1112.11(2026:08:21 01:34:51). Not you? Call 18002584455/8468001111-BOB"

    @Test fun `bob debit parses`() {
        val p = SmsParser.parse("JK-BOBSMS-S", bobDebit)
        assertEquals(6_000L, p?.amountPaise)
        assertEquals("OUT", p?.direction)
        assertEquals("crazzyproduct@axl", p?.merchant)
        assertEquals("1234567890", p?.accountHint)
        assertEquals("858963521470", p?.referenceNo)
        assertEquals("bob-upi-debit-1", p?.templateId)
    }

    @Test fun `bob long gateway vpa parses`() {
        val body = bobDebit.replace(
            "crazzyproduct@axl",
            "roppentransport123456.rzp-tqqchhyc9k9uisqrv2@rxairtel",
        )
        assertEquals(
            "roppentransport123456.rzp-tqqchhyc9k9uisqrv2@rxairtel",
            SmsParser.parse("JK-BOBSMS-S", body)?.merchant,
        )
    }

    // --- Bank of Baroda UPI credit ---

    @Test fun `bob credit parses as income without merchant`() {
        val body = "Dear BOB UPI User: Your account is credited with INR 3400.00 on " +
            "2026-08-05 02:23:26 PM by UPI Ref No 522199887766; AvlBal: Rs3959.11 - BOB"
        val p = SmsParser.parse("JK-BOBSMS-S", body)
        assertEquals(340_000L, p?.amountPaise)
        assertEquals("IN", p?.direction)
        assertNull(p?.merchant)
        assertEquals("522199887766", p?.referenceNo)
        assertEquals("bob-upi-credit-1", p?.templateId)
    }

    // --- cross-bank gating ---

    @Test fun `bob body with hdfc sender does not parse`() =
        assertNull(SmsParser.parse("JD-HDFCBK-S", bobDebit))

    @Test fun `hdfc body with bob sender does not parse`() =
        assertNull(SmsParser.parse("JK-BOBSMS-S", hdfcSent))
}
