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

    /** Built from Rajnikant's masked real samples — see BankTemplates. */
    val templates: List<SmsTemplate> = BankTemplates.all

    /**
     * Which bank a sender id belongs to ("VM-BOBSMS-S" -> BOB, "AD-HDFCBK-S" -> HDFC),
     * or null for everything else. Liberal on purpose: this gates which messages the
     * capture pipeline LOOKS at (promos from these banks included); the templates decide
     * what actually parses.
     */
    fun senderBank(sender: String): String? {
        val s = sender.uppercase()
        return when {
            s.contains("HDFC") -> "HDFC"
            s.contains("BOB") -> "BOB"
            else -> null
        }
    }

    fun isBankSender(sender: String): Boolean = senderBank(sender) != null

    fun parse(sender: String, body: String, templateList: List<SmsTemplate> = templates): ParsedSms? {
        val bank = senderBank(sender) ?: return null
        for (template in templateList) {
            if (template.bank != bank) continue
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
