// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

/**
 * Payment-app notification parsing. Like SmsParser, templates are added by the architect
 * from real masked samples (phase 7 stage B); until then the list is empty and stage A
 * only HARVESTS candidate notifications for review.
 *
 * Template regex group convention (matched against "title\ntext"):
 * named groups `amount` (required), `merchant` (optional).
 */
data class NotificationTemplate(
    val id: String,          // stable, e.g. "gpay-paid-1"
    val packageName: String, // exact app package this template belongs to
    val direction: String,   // "IN" | "OUT"
    val regex: Regex,
)

data class ParsedNotification(
    val templateId: String,
    val amountPaise: Long,
    val direction: String,
    val merchant: String?,
)

object NotificationParser {

    /**
     * Apps whose notifications the listener LOOKS at. Everything else is ignored
     * before any text is read. Grows only by architect decision.
     */
    val paymentPackages: Set<String> = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.bankofbaroda.mconnect",              // BoB World
        "com.snapwork.hdfc",                      // HDFC Bank MobileBanking
        "com.hdfcbank.payzapp",                   // HDFC PayZapp
    )

    fun isPaymentApp(packageName: String): Boolean = packageName in paymentPackages

    /** Filled by the architect in stage B, built from masked real samples. */
    val templates: List<NotificationTemplate> = emptyList()

    fun parse(
        packageName: String,
        title: String,
        text: String,
        templateList: List<NotificationTemplate> = templates,
    ): ParsedNotification? {
        if (!isPaymentApp(packageName)) return null
        val body = "$title\n$text"
        for (template in templateList) {
            if (template.packageName != packageName) continue
            val match = template.regex.find(body) ?: continue
            val groups = match.groups as? MatchNamedGroupCollection ?: continue
            val amountPaise = groups.valueOf("amount")?.let(SmsParser::inrToPaise) ?: continue
            return ParsedNotification(
                templateId = template.id,
                amountPaise = amountPaise,
                direction = template.direction,
                merchant = groups.valueOf("merchant")?.trim()?.ifBlank { null },
            )
        }
        return null
    }

    private fun MatchNamedGroupCollection.valueOf(name: String): String? =
        try {
            get(name)?.value
        } catch (_: IllegalArgumentException) {
            null
        }
}
