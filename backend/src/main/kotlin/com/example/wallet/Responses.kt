package com.example.wallet

import org.springframework.data.domain.Page
import java.math.BigDecimal

fun BigDecimal.money(): String = setScale(2).toPlainString()

fun Wallet.response(bonusProgress: Bonus?) = mapOf(
    "userId" to userId,
    "currency" to "EUR",
    "cash" to cash.money(),
    "bonus" to bonus.money(),
    "bonusProgress" to bonusProgress?.let {
        mapOf(
            "status" to it.status,
            "grantedAmount" to it.grantedAmount.money(),
            "wageredAmount" to it.wageredAmount.money(),
            "targetAmount" to it.targetAmount.money(),
            "expiresAt" to it.expiresAt
        )
    }
)

fun Deposit.response() = mapOf(
    "depositId" to id,
    "userId" to userId,
    "amount" to amount.money(),
    "currency" to "EUR",
    "status" to status,
    "createdAt" to createdAt,
    "completedAt" to completedAt
)

fun DepositCallback.response() = mapOf(
    "id" to id,
    "userId" to userId,
    "transactionId" to transactionId,
    "depositId" to depositId,
    "amount" to amount.money(),
    "currency" to "EUR",
    "status" to status,
    "createdAt" to createdAt,
    "completedAt" to if (status == "COMPLETED") createdAt else null
)

fun Round.response(wallet: Wallet, bonus: Bonus?) = mapOf(
    "roundId" to id,
    "userId" to userId,
    "currency" to "EUR",
    "stake" to stake.money(),
    "stakeCash" to stakeCash.money(),
    "stakeBonus" to stakeBonus.money(),
    "payoutPercent" to payoutPercent,
    "payout" to payout.money(),
    "payoutCash" to payoutCash.money(),
    "payoutBonus" to payoutBonus.money(),
    "createdAt" to createdAt,
    "wallet" to wallet.response(bonus)
)

fun LedgerEntry.response() = mapOf(
    "id" to id,
    "userId" to userId,
    "type" to type,
    "amount" to (cashDelta + bonusDelta).money(),
    "cashDelta" to cashDelta.money(),
    "bonusDelta" to bonusDelta.money(),
    "cashAfter" to cashAfter.money(),
    "bonusAfter" to bonusAfter.money(),
    "currency" to "EUR",
    "depositId" to depositId,
    "roundId" to roundId,
    "bonusId" to bonusId,
    "createdAt" to createdAt,
    "status" to "COMPLETED",
    "completedAt" to createdAt
)

fun <T : Any> Page<T>.response(convert: (T) -> Any) = mapOf(
    "items" to content.map(convert),
    "page" to number,
    "size" to size,
    "totalElements" to totalElements,
    "totalPages" to totalPages
)
