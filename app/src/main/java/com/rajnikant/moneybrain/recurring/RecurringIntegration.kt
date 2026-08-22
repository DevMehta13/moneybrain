package com.rajnikant.moneybrain.recurring

import com.rajnikant.moneybrain.capture.ActionKinds
import com.rajnikant.moneybrain.capture.ActionPayload
import com.rajnikant.moneybrain.capture.PayloadKeys
import com.rajnikant.moneybrain.data.ActionEntity
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import com.rajnikant.moneybrain.data.RecurringEntity
import com.rajnikant.moneybrain.data.TransactionEntity
import com.rajnikant.moneybrain.money.Money
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first

fun RecurringEntity.toItem() = RecurringItem(id, name, merchantKey, expectedAmountPaise, cadence, nextDue, anchorDay, bucketId, status, createdAt)

suspend fun applyRecurringMatch(database: MoneyBrainDatabase, transaction: TransactionEntity) {
    if (transaction.direction != "OUT") return
    val match = RecurringMatcher.match(transaction.merchant, transaction.amountPaise, transaction.direction,
        Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
        database.recurringDao().observeAll().first().map { it.toItem() }) ?: return
    val oldDue = match.nextDueIso
    database.recurringDao().setNextDue(match.id, RecurringMath.advance(oldDue, match.cadence, match.anchorDay))
    if (transaction.bucketId == null) database.transactionDao().setBucket(transaction.id, match.bucketId)
    database.actionDao().insert(ActionEntity(kind = ActionKinds.RECURRING_MATCHED, targetType = "recurring", targetId = match.id,
        description = "Matched ${match.name}: ${Money.formatPaise(transaction.amountPaise)}",
        payload = ActionPayload.encode(mapOf(PayloadKeys.OLD_NEXT_DUE to oldDue)), createdAt = System.currentTimeMillis()))
}
