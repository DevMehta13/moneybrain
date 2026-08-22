// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.money

/**
 * The single authority for money representation in Money Brain.
 *
 * Amounts are ALWAYS integer paise (Long): ₹123.45 == 12345L.
 * Nothing outside this file formats or parses rupee strings.
 */
object Money {

    /** Formats paise as Indian-grouped rupees: 12345678L -> "₹1,23,456.78". Whole rupees drop ".00". */
    fun formatPaise(paise: Long): String {
        val negative = paise < 0
        // Long.MIN_VALUE has no positive counterpart; unreachable for real amounts, but stay total.
        val abs = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else if (negative) -paise else paise
        val rupees = abs / 100
        val fraction = abs % 100
        val grouped = groupIndian(rupees.toString())
        val sign = if (negative) "-" else ""
        return if (fraction == 0L) "$sign₹$grouped"
        else "$sign₹$grouped.${fraction.toString().padStart(2, '0')}"
    }

    /**
     * Parses user input to paise. Accepts optional ₹, commas, spaces, minus, and up to two decimals:
     * "300" -> 30000, "299.5" -> 29950, "₹1,23,456.78" -> 12345678. Returns null for anything else.
     */
    fun parseToPaise(text: String): Long? {
        var s = text.trim().replace("₹", "").replace(",", "").replace(" ", "")
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("-")) {
            negative = true
            s = s.substring(1)
        }
        if (s.isEmpty() || s.count { it == '.' } > 1) return null
        val parts = s.split(".")
        val rupeePart = parts[0]
        val paisePart = if (parts.size == 2) parts[1] else ""
        if (rupeePart.isEmpty() && paisePart.isEmpty()) return null
        if (rupeePart.any { !it.isDigit() } || paisePart.any { !it.isDigit() }) return null
        if (paisePart.length > 2) return null
        if (rupeePart.length > 13) return null // beyond any real amount; also guards overflow
        val rupees = if (rupeePart.isEmpty()) 0L else rupeePart.toLong()
        val paise = when (paisePart.length) {
            0 -> 0L
            1 -> paisePart.toLong() * 10
            else -> paisePart.toLong()
        }
        val total = rupees * 100 + paise
        return if (negative) -total else total
    }

    /** Indian digit grouping: last 3 digits, then pairs. "1234567" -> "12,34,567". */
    private fun groupIndian(digits: String): String {
        val n = digits.length
        if (n <= 3) return digits
        val head = digits.substring(0, n - 3)
        val tail = digits.substring(n - 3)
        val groups = ArrayList<String>()
        var i = head.length
        while (i > 0) {
            val start = if (i - 2 > 0) i - 2 else 0
            groups.add(0, head.substring(start, i))
            i = start
        }
        return groups.joinToString(",") + "," + tail
    }
}
