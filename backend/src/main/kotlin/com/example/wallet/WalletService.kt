package com.example.wallet

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class WalletService(
    private val players: PlayerRepository,
    private val wallets: WalletRepository,
    private val deposits: DepositRepository,
    private val depositCallbacks: DepositCallbackRepository,
    private val bonuses: BonusRepository,
    private val rounds: RoundRepository,
    private val ledger: LedgerRepository,
    private val transactionHistory: TransactionHistoryStore,
    private val idempotency: IdempotencyStore,
    private val entityManager: EntityManager
) {
    private val random = SecureRandom()

    @Transactional
    fun register(): Any {
        val player = players.saveAndFlush(Player())

        return wallets.save(Wallet(player.id)).response(null)
    }

    @Transactional
    fun wallet(userId: UUID): Any {
        val wallet = lock(userId)
        val bonus = bonuses.findByUserId(userId)
        expire(wallet, bonus)

        return wallet.response(bonus)
    }

    @Transactional
    fun createDeposit(userId: UUID, amount: BigDecimal, key: UUID): Any {
        val wallet = lock(userId)
        idempotency.replay(userId, "DEPOSIT", key, amount)?.let { return it }

        if (amount !in listOf(BigDecimal("10.00"), BigDecimal("20.00"), BigDecimal("50.00"))) {
            throw ApiException(400, "VALIDATION_ERROR", "Deposit must be 10.00, 20.00 or 50.00 EUR")
        }

        expire(wallet, bonuses.findByUserId(userId))

        val deposit = deposits.save(Deposit(userId = userId, amount = amount))
        val response = deposit.response()

        idempotency.save(userId, "DEPOSIT", key, amount, 201, response)

        return response
    }

    @Transactional
    fun processDepositCallback(userId: UUID, transactionId: UUID, amount: BigDecimal): CallbackOutcome {
        val wallet = lock(userId)
        val deposit = deposits.findByIdAndUserId(transactionId, userId)

        if (deposit == null) {
            val callback = depositCallbacks.save(
                DepositCallback(userId = userId, transactionId = transactionId, amount = amount, status = "NOT_FOUND")
            )
            return CallbackOutcome(
                404,
                mapOf(
                    "code" to "DEPOSIT_NOT_FOUND", "message" to "Deposit not found",
                    "fieldErrors" to emptyList<Any>(), "callback" to callback.response()
                )
            )
        }

        if (deposit.amount.compareTo(amount) != 0) {
            val callback = depositCallbacks.save(
                DepositCallback(
                    userId = userId, transactionId = transactionId, depositId = deposit.id,
                    amount = amount, status = "WRONG_TRANSACTION"
                )
            )
            return CallbackOutcome(
                409,
                mapOf(
                    "code" to "WRONG_TRANSACTION", "message" to "Transaction amount does not match",
                    "fieldErrors" to emptyList<Any>(), "callback" to callback.response()
                )
            )
        }

        if (deposit.status == "COMPLETED") {
            val callback = depositCallbacks.save(
                DepositCallback(
                    userId = userId, transactionId = transactionId, depositId = deposit.id,
                    amount = amount, status = "DUPLICATED"
                )
            )
            return CallbackOutcome(
                200,
                mapOf("result" to "Duplicated", "callback" to callback.response(), "deposit" to deposit.response())
            )
        }

        val bonus = bonuses.findByUserId(userId)
        expire(wallet, bonus)
        deposit.status = "COMPLETED"
        deposit.completedAt = Instant.now()
        change(wallet, "DEPOSIT", deposit.amount, ZERO, depositId = deposit.id)

        if (bonus == null && deposit.amount >= BigDecimal("20.00")) {
            val granted = bonuses.save(
                Bonus(userId = userId, depositId = deposit.id, grantedAmount = deposit.amount.min(BigDecimal("100.00")))
            )
            change(wallet, "BONUS_GRANTED", ZERO, granted.grantedAmount, depositId = deposit.id, bonusId = granted.id)
        }

        val callback = depositCallbacks.save(
            DepositCallback(
                userId = userId, transactionId = transactionId, depositId = deposit.id,
                amount = amount, status = "COMPLETED"
            )
        )
        return CallbackOutcome(
            200,
            mapOf("result" to "Completed", "callback" to callback.response(), "deposit" to deposit.response())
        )
    }

    @Transactional(readOnly = true)
    fun depositCallbacks(userId: UUID, page: Int, size: Int): Any {
        requirePlayer(userId)
        return depositCallbacks.findByUserId(userId, pagination(page, size)).response { it.response() }
    }

    @Transactional(readOnly = true)
    fun deposit(userId: UUID, depositId: UUID): Any {
        requirePlayer(userId)

        return deposits.findByIdAndUserId(depositId, userId)?.response()
            ?: throw ApiException(404, "DEPOSIT_NOT_FOUND", "Deposit not found")
    }

    @Transactional(readOnly = true)
    fun deposits(userId: UUID, page: Int, size: Int, status: String?): Any {
        requirePlayer(userId)
        val pageable = pagination(page, size)
        val result = when (status) {
            null -> deposits.findByUserId(userId, pageable)
            "PENDING", "COMPLETED" -> deposits.findByUserIdAndStatus(userId, status, pageable)
            else -> throw ApiException(400, "VALIDATION_ERROR", "Invalid deposit status")
        }

        return result.response { it.response() }
    }

    @Transactional(readOnly = true)
    fun ledger(userId: UUID, page: Int, size: Int): Any {
        requirePlayer(userId)
        pagination(page, size)
        return transactionHistory.find(userId, page, size)
    }

    @Transactional
    fun bet(userId: UUID, amount: BigDecimal, key: UUID): Any {
        val version = wallets.findVersionByUserId(userId)
            ?: throw ApiException(404, "USER_NOT_FOUND", "Player not found")
        val wallet = lock(userId)
        idempotency.replay(userId, "BET", key, amount)?.let { return it }

        if (wallet.version != version) {
            throw ApiException(409, "WALLET_CHANGED", "The wallet changed while this bet was being processed")
        }

        val bonus = bonuses.findByUserId(userId)
        expire(wallet, bonus)

        if (bonus?.status == "ACTIVE" && amount > BigDecimal("5.00")) {
            throw ApiException(409, "BONUS_BET_LIMIT_EXCEEDED", "Maximum stake with an active bonus is 5.00 EUR")
        }

        if (wallet.cash + wallet.bonus < amount) {
            throw ApiException(409, "INSUFFICIENT_FUNDS", "Insufficient funds")
        }

        entityManager.lock(wallet, LockModeType.PESSIMISTIC_FORCE_INCREMENT)
        val stakeCash = wallet.cash.min(amount)
        val stakeBonus = amount - stakeCash
        val payoutPercent = when (random.nextInt(100)) {
            in 0..50 -> 0
            in 51..75 -> 50
            in 76..90 -> 100
            in 91..95 -> 150
            in 96..98 -> 200
            else -> 400
        }
        val payout = amount.multiply(BigDecimal(payoutPercent)).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        val payoutCash = payout.multiply(stakeCash).divide(amount, 2, RoundingMode.HALF_UP)
        val round = rounds.save(
            Round(
                userId = userId, stake = amount, stakeCash = stakeCash, stakeBonus = stakeBonus,
                payoutPercent = payoutPercent, payout = payout, payoutCash = payoutCash, payoutBonus = payout - payoutCash
            )
        )

        change(wallet, "BET", -stakeCash, -stakeBonus, roundId = round.id)

        if (payout > ZERO) {
            change(wallet, "PAYOUT", round.payoutCash, round.payoutBonus, roundId = round.id)
        }

        if (bonus?.status == "ACTIVE") {
            bonus.wageredAmount += amount
            if (bonus.wageredAmount >= bonus.targetAmount) {
                bonus.status = "COMPLETED"
                bonus.closedAt = Instant.now()
                change(wallet, "BONUS_CONVERTED", wallet.bonus, -wallet.bonus, bonusId = bonus.id)
            }
        }

        val response = round.response(wallet, bonus)
        idempotency.save(userId, "BET", key, amount, 200, response)

        return response
    }

    @Transactional
    fun expireBonus(userId: UUID) {
        val wallet = lock(userId)
        expire(wallet, bonuses.findByUserId(userId))
    }

    private fun expire(wallet: Wallet, bonus: Bonus?) {
        if (bonus?.status == "ACTIVE" && !bonus.expiresAt.isAfter(Instant.now())) {
            bonus.status = "EXPIRED"
            bonus.closedAt = Instant.now()
            change(wallet, "BONUS_EXPIRED", ZERO, -wallet.bonus, bonusId = bonus.id)
        }
    }

    private fun change(
        wallet: Wallet,
        type: String,
        cash: BigDecimal,
        bonus: BigDecimal,
        depositId: UUID? = null,
        roundId: UUID? = null,
        bonusId: UUID? = null
    ) {
        wallet.cash += cash
        wallet.bonus += bonus
        ledger.save(
            LedgerEntry(
                userId = wallet.userId, type = type, cashDelta = cash, bonusDelta = bonus,
                cashAfter = wallet.cash, bonusAfter = wallet.bonus,
                depositId = depositId, roundId = roundId, bonusId = bonusId
            )
        )
    }

    private fun lock(userId: UUID): Wallet = wallets.lockByUserId(userId)
        ?: throw ApiException(404, "USER_NOT_FOUND", "Player not found")

    private fun requirePlayer(userId: UUID) {
        if (!players.existsById(userId)) throw ApiException(404, "USER_NOT_FOUND", "Player not found")
    }

    private fun pagination(page: Int, size: Int): PageRequest {
        if (page < 0 || size !in 1..100 || page.toLong() * size > Int.MAX_VALUE) {
            throw ApiException(400, "VALIDATION_ERROR", "Invalid pagination")
        }

        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
    }
}

data class CallbackOutcome(val status: Int, val body: Any)
