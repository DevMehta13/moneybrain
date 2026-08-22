// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.money

/**
 * Account balance tracking (owner decision 2026-08-23). The app never guesses a balance:
 * an account is "tracked" only after the owner states its real balance once (a snapshot).
 * From then on:
 *
 *   balance = latest snapshot + sum of signed transactions AFTER the snapshot moment
 *
 * A snapshot states the balance AFTER everything up to and including its instant, so a
 * transaction with occurredAt == asOfMillis is already inside the stated number and is
 * never added again. "Correct balance" simply adds a newer snapshot; deleting a snapshot
 * falls back to the previous one. Balances are computed, never stored (core rule 2).
 */
data class BalanceSnapshot(
    val id: Long,
    val accountId: Long,
    val balancePaise: Long,
    val asOfMillis: Long,
)

/** The slice of a transaction that moves a balance. */
data class BalanceTxn(
    val accountId: Long,
    val amountPaise: Long,
    val direction: String, // "IN" | "OUT"
    val occurredAt: Long,
)

data class TotalBalance(
    /** Sum over tracked accounts only; null when NO account is tracked. */
    val totalPaise: Long?,
    val trackedAccountIds: List<Long>,
    val untrackedAccountIds: List<Long>,
)

object BalanceMath {

    /** IN adds, OUT subtracts. Anything else is a programming error, not data. */
    fun signedPaise(direction: String, amountPaise: Long): Long {
        require(amountPaise >= 0) { "negative amount" }
        return when (direction) {
            "IN" -> amountPaise
            "OUT" -> -amountPaise
            else -> throw IllegalArgumentException("unknown direction: $direction")
        }
    }

    /** Latest snapshot wins: newest asOfMillis, then highest id (two corrections in one instant). */
    fun latestSnapshot(accountId: Long, snapshots: List<BalanceSnapshot>): BalanceSnapshot? =
        snapshots.filter { it.accountId == accountId }
            .maxWithOrNull(compareBy({ it.asOfMillis }, { it.id }))

    /** null = untracked (no snapshot yet) — shown as "not tracked", never as ₹0. */
    fun accountBalance(
        accountId: Long,
        snapshots: List<BalanceSnapshot>,
        transactions: List<BalanceTxn>,
    ): Long? {
        val snap = latestSnapshot(accountId, snapshots) ?: return null
        return snap.balancePaise + transactions
            .filter { it.accountId == accountId && it.occurredAt > snap.asOfMillis }
            .sumOf { signedPaise(it.direction, it.amountPaise) }
    }

    fun totalBalance(
        accountIds: List<Long>,
        snapshots: List<BalanceSnapshot>,
        transactions: List<BalanceTxn>,
    ): TotalBalance {
        val tracked = ArrayList<Long>()
        val untracked = ArrayList<Long>()
        var total = 0L
        for (id in accountIds) {
            val balance = accountBalance(id, snapshots, transactions)
            if (balance == null) {
                untracked.add(id)
            } else {
                tracked.add(id)
                total += balance
            }
        }
        return TotalBalance(if (tracked.isEmpty()) null else total, tracked, untracked)
    }

    /**
     * What a correction changed: stated real balance minus what the app had computed.
     * Positive = money the app never saw (cash in, interest); negative = unseen spending.
     * Stored on the snapshot row for display only — never used in any computation.
     */
    fun correctionDelta(statedPaise: Long, computedPaise: Long): Long = statedPaise - computedPaise
}
