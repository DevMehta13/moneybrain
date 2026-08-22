package com.rajnikant.moneybrain.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val createdAt: Long,
    val bankCode: String? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val bucketId: Long? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["fingerprint"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: String,
    val accountId: Long,
    val categoryId: Long?,
    val merchant: String?,
    val occurredAt: Long,
    val notes: String?,
    val source: String = "MANUAL",
    val fingerprint: String? = null,
    val referenceNo: String? = null,
    val createdAt: Long,
    val bucketId: Long? = null,
    val tripId: Long? = null,
)

@Entity(tableName = "buckets")
data class BucketEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val sortOrder: Int, val createdAt: Long)

@Entity(tableName = "bucket_plan", foreignKeys = [ForeignKey(entity = BucketEntity::class, parentColumns = ["id"], childColumns = ["bucketId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["bucketId"])])
data class BucketPlanEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val bucketId: Long, val kind: String, val value: Long, val sortOrder: Int)

@Entity(tableName = "bucket_entries", foreignKeys = [ForeignKey(entity = BucketEntity::class, parentColumns = ["id"], childColumns = ["bucketId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["bucketId"]), Index(value = ["sourceTransactionId"])])
data class BucketEntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val bucketId: Long, val amountPaise: Long, val kind: String, val sourceTransactionId: Long?, val counterpartEntryId: Long? = null, val note: String? = null, val createdAt: Long)

@Entity(tableName = "balance_snapshots", foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["accountId", "asOfMillis"])])
data class BalanceSnapshotEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val accountId: Long, val balancePaise: Long, val asOfMillis: Long, val deltaPaise: Long?, val createdAt: Long)

@Entity(tableName = "split_dismissed")
data class SplitDismissedEntity(@PrimaryKey val transactionId: Long, val dismissedAt: Long)

@Entity(tableName = "recurring", indices = [Index(value = ["status", "nextDue"])])
data class RecurringEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val merchantKey: String?, val expectedAmountPaise: Long, val cadence: String, val nextDue: String, val anchorDay: Int, val bucketId: Long?, val status: String, val createdAt: Long)

@Entity(tableName = "recurring_dismissed")
data class RecurringDismissedEntity(@PrimaryKey val merchantKey: String, val dismissedAt: Long)

@Entity(tableName = "trips") data class TripEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val startedAt: Long, val endedAt: Long?, val createdAt: Long)
@Entity(tableName = "people") data class PersonEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long)
@Entity(tableName = "person_ledger", foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["personId"]), Index(value = ["transactionId"])]) data class PersonLedgerEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val personId: Long, val amountPaise: Long, val kind: String, val transactionId: Long?, val note: String?, val createdAt: Long)

@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["merchantKey"], unique = true),
        Index(value = ["categoryId"]),
    ],
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantKey: String,
    val categoryId: Long,
    val createdAt: Long,
)

@Entity(tableName = "actions", indices = [Index(value = ["createdAt"])])
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val targetType: String,
    val targetId: Long,
    val description: String,
    val payload: String,
    val createdAt: Long,
    val undoneAt: Long? = null,
)

@Entity(tableName = "unparsed_sms", indices = [Index(value = ["receivedAt"])])
data class UnparsedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val resolvedAt: Long? = null,
)
