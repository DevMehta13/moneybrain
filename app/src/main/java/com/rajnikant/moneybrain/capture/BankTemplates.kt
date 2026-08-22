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
