// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

import com.rajnikant.moneybrain.money.Money

/**
 * Bank SMS parsing. Templates are added by the architect from real masked samples
 * (stage B); until then the list is empty and every bank SMS reports as unrecognised.
 *
 * Template regex group convention: named groups `amount` (required),
 * `merchant`, `account`, `ref` (each optional).
 */
data class SmsTemplate(
    val id: String,        // stable, e.g. "bob-upi-debit-1"
    val bank: String,      // "BOB" | "HDFC"
    val direction: String, // "IN" | "OUT"
    val regex: Regex,
)

data class ParsedSms(
    val bank: String,
    val templateId: String,
    val amountPaise: Long,
    val direction: String,
    val merchant: String?,
    val accountHint: String?,
    val referenceNo: String?,
)

object SmsParser {

    /** Filled by the architect in stage B, built from masked real samples. */
    val templates: List<SmsTemplate> = emptyList()

    /**
     * Liberal on purpose: this gates which messages the capture pipeline LOOKS at
     * (promos from the same banks included); the templates decide what actually parses.
     * Sender ids look like "VM-BOBSMS", "AD-HDFCBK-S", "BOBTXN".
     */
    fun isBankSender(sender: String): Boolean {
        val s = sender.uppercase()
        return s.contains("BOB") || s.contains("HDFC")
    }

    fun parse(sender: String, body: String, templateList: List<SmsTemplate> = templates): ParsedSms? {
        if (!isBankSender(sender)) return null
        for (template in templateList) {
            val match = template.regex.find(body) ?: continue
            val groups = match.groups as? MatchNamedGroupCollection ?: continue
            val amountPaise = groups.valueOf("amount")?.let(::inrToPaise) ?: continue
            return ParsedSms(
                bank = template.bank,
                templateId = template.id,
                amountPaise = amountPaise,
                direction = template.direction,
                merchant = groups.valueOf("merchant")?.trim()?.ifBlank { null },
                accountHint = groups.valueOf("account"),
                referenceNo = groups.valueOf("ref"),
            )
        }
        return null
    }

    /** "Rs.1,234.50" / "INR 450" / "₹450.5" -> paise via the Money authority. */
    fun inrToPaise(raw: String): Long? {
        val cleaned = raw.trim().replace(Regex("""(?i)^(rs\.?|inr|₹)\s*"""), "")
        return Money.parseToPaise(cleaned)
    }

    // Matcher.group(name) throws when the pattern lacks that group; treat as absent.
    private fun MatchNamedGroupCollection.valueOf(name: String): String? =
        try {
            get(name)?.value
        } catch (_: IllegalArgumentException) {
            null
        }
}
