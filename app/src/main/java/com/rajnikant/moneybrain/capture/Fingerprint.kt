// ARCHITECT-OWNED — integrate and call this file; do not alter its logic (see AGENTS.md).
package com.rajnikant.moneybrain.capture

/**
 * Deterministic dedupe key for captured transactions (SMS now, notifications in phase 7).
 * Stored in TransactionEntity.fingerprint (UNIQUE index): a second capture of the same
 * payment computes the same fingerprint and is rejected by the database.
 *
 * With a bank reference number the key is exact. Without one we fall back to a
 * two-minute time bucket + amount + direction + account hint. Known limitation:
 * two identical no-reference payments inside the same two minutes collide (the second
 * is treated as a duplicate). Accepted for v1; the activity log makes it visible.
 */
object Fingerprint {

    private const val BUCKET_MS = 120_000L

    fun of(
        amountPaise: Long,
        direction: String,
        accountHint: String?,
        occurredAtMillis: Long,
        referenceNo: String?,
    ): String =
        if (!referenceNo.isNullOrBlank()) {
            "ref:${referenceNo.trim().uppercase()}:$amountPaise:$direction"
        } else {
            "tw:${occurredAtMillis / BUCKET_MS}:$amountPaise:$direction:${accountHint.orEmpty()}"
        }
}
