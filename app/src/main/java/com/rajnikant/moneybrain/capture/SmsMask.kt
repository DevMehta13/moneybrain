// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

/**
 * Privacy mask for SMS bodies shown on the capture screen or copied out as samples.
 *
 * Policy: digit runs of 4+ (account numbers, card digits, UPI refs, phone numbers) become
 * same-length runs of 'X' — length is preserved so message STRUCTURE stays visible for
 * template building. Amounts (marked by Rs/INR/₹) are kept: they are needed to design
 * amount patterns and are not identifying. Short runs (dates, times) pass untouched.
 *
 * Raw (unmasked) bodies must never leave the phone.
 */
object SmsMask {

    private val amountPattern = Regex("""(?i)(?:rs\.?|inr|₹)\s?[\d,]+(?:\.\d{1,2})?""")
    private val digitRun = Regex("""\d{4,}""")

    fun mask(body: String): String {
        val keep = amountPattern.findAll(body).map { it.range }.toList()
        return digitRun.replace(body) { match ->
            val protected = keep.any { it.first <= match.range.first && match.range.last <= it.last }
            if (protected) match.value else "X".repeat(match.value.length)
        }
    }
}
