// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.people

/**
 * Splitting and person-ledger math. Integer paise throughout.
 *
 * THE LEDGER SIGN CONVENTION (one rule, everywhere):
 *   positive amount  = this person owes me more
 *   negative amount  = this person owes me less (or I owe them)
 *   balance          = plain sum of a person's ledger amounts
 *
 * Kinds and their signs:
 *   SPLIT      + their share of something I paid
 *   LENT       + money I handed them
 *   I_OWE      − something they covered for me
 *   SETTLEMENT − when they pay me back; + when I pay them back
 *
 * So "Rahul owes me ₹300" is balance +30000; "I owe Priya ₹500" is balance −50000;
 * a settled person is exactly 0.
 */
object LedgerKinds {
    const val SPLIT = "SPLIT"
    const val LENT = "LENT"
    const val I_OWE = "I_OWE"
    const val SETTLEMENT = "SETTLEMENT"
}

object SplitMath {

    /**
     * Divides a total into `parts` shares that sum EXACTLY to the total.
     * ₹1,000 across 3 → [₹333.34, ₹333.33, ₹333.33] — the leftover paise go to the
     * earliest shares, never lost, never invented.
     */
    fun equalShares(totalPaise: Long, parts: Int): List<Long> {
        require(totalPaise >= 0) { "negative total" }
        require(parts > 0) { "need at least one part" }
        val base = totalPaise / parts
        val remainder = (totalPaise % parts).toInt()
        return List(parts) { index -> base + if (index < remainder) 1L else 0L }
    }

    /** Custom split sanity: every share positive, and the others' shares never exceed the total. */
    fun validCustomShares(totalPaise: Long, othersShares: List<Long>): Boolean =
        othersShares.isNotEmpty() &&
            othersShares.all { it > 0 } &&
            othersShares.sum() <= totalPaise

    /** Balance under the sign convention above: just the sum. */
    fun balance(ledgerAmounts: List<Long>): Long = ledgerAmounts.sum()

    /**
     * The signed SETTLEMENT amount that zeroes a balance: they owe me +X → settlement −X
     * (they paid me); I owe them −X → settlement +X (I paid them). Zero balance → 0.
     */
    fun settlementAmount(balancePaise: Long): Long = -balancePaise
}
