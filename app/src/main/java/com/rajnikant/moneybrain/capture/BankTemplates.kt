// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

/**
 * Real-format templates for Bank of Baroda and HDFC, built 2026-08-22 from masked samples
 * of Rajnikant's actual inbox. Group convention per SmsTemplate: amount (required),
 * merchant / account / ref (optional).
 *
 * Deliberately NOT matched (verified by tests): HDFC "Mandate Set" and "mandate cancelled"
 * notices (no money moves), promotional messages from bank sender ids, OTPs.
 */
object BankTemplates {

    val all: List<SmsTemplate> = listOf(

        // HDFC UPI debit, multi-line. Covers both the plain form ("Sent … From … To … On
        // <date> …") and the mandate-execution form ("UPI Mandate: Sent … from … To …
        // <date> …", no "On", lowercase from, no "*" before the account digits).
        SmsTemplate(
            id = "hdfc-upi-sent-1",
            bank = "HDFC",
            direction = "OUT",
            regex = Regex(
                """(?i)Sent\s+(?<amount>Rs\.?\s?[\d,]+(?:\.\d{1,2})?)\s*\r?\n\s*from\s+HDFC\s+Bank\s+A/c\s+\*?(?<account>\w+)\s*\r?\n\s*To\s+(?<merchant>[^\r\n]+?)\s*\r?\n\s*(?:On\s+)?\d{1,2}/\d{1,2}/\d{2,4}\s*\r?\n\s*Ref[ :]*(?<ref>\w+)"""
            ),
        ),

        // Bank of Baroda UPI debit, single line:
        // "Rs.60.00 Dr. from A/C 1234567890 and Cr. to <vpa>. Ref:<digits>. AvlBal:…"
        SmsTemplate(
            id = "bob-upi-debit-1",
            bank = "BOB",
            direction = "OUT",
            regex = Regex(
                """(?i)(?<amount>Rs\.?\s?[\d,]+(?:\.\d{1,2})?)\s+Dr\.?\s+from\s+A/C\s+(?<account>\w+)\s+and\s+Cr\.?\s+to\s+(?<merchant>\S+?)\.?\s+Ref[ :]+(?<ref>\w+)"""
            ),
        ),

        // HDFC UPI credit, multi-line:
        // "Credit Alert!\nRs.271.00 credited to HDFC Bank A/c 123456 on 20-07-26
        //  from VPA faasos.payu@indus (UPI 520199887766)"
        SmsTemplate(
            id = "hdfc-upi-credit-1",
            bank = "HDFC",
            direction = "IN",
            regex = Regex(
                """(?i)Credit\s+Alert!\s*\r?\n?\s*(?<amount>Rs\.?\s?[\d,]+(?:\.\d{1,2})?)\s+credited\s+to\s+HDFC\s+Bank\s+A/c\s+(?<account>\w+)\s+on\s+\d{1,2}-\d{1,2}-\d{2,4}\s+from\s+VPA\s+(?<merchant>\S+)\s+\(UPI\s*(?<ref>\w+)\)"""
            ),
        ),

        // HDFC NEFT deposit (salary arrives this way), single line:
        // "Update! INR 80,757.00 deposited in HDFC Bank A/c 123456 on 24-JUL-26 for
        //  NEFT Cr-<bank>-Salary for <MON> <employer>-<name>-<ref>.Avl bal INR …"
        // The whole "for …" narrative becomes the merchant — phase 3 salary detection
        // keys off "Salary" appearing in it. No separate ref group (embedded in narrative).
        SmsTemplate(
            id = "hdfc-neft-credit-1",
            bank = "HDFC",
            direction = "IN",
            regex = Regex(
                """(?i)Update!\s+(?<amount>(?:Rs\.?|INR)\s?[\d,]+(?:\.\d{1,2})?)\s+deposited\s+in\s+HDFC\s+Bank\s+A/c\s+(?<account>\w+)\s+on\s+\S+\s+for\s+(?<merchant>.+?)\s*\.?\s*Avl\s+bal"""
            ),
        ),

        // Bank of Baroda UPI credit, single line:
        // "…credited with INR 3400.00 on <timestamp> by UPI Ref No <digits>; AvlBal:…"
        // No merchant information in this format.
        SmsTemplate(
            id = "bob-upi-credit-1",
            bank = "BOB",
            direction = "IN",
            regex = Regex(
                """(?i)credited\s+with\s+(?<amount>(?:Rs\.?|INR)\s?[\d,]+(?:\.\d{1,2})?)\s+on\b[\s\S]{0,40}?UPI\s+Ref\s+No[ :]*(?<ref>\w+)"""
            ),
        ),
    )
}
