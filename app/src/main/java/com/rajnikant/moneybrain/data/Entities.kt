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
)

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
