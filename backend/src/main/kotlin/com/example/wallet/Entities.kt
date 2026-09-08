package com.example.wallet

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

val ZERO: BigDecimal = BigDecimal("0.00")

@Entity
@Table(name = "players")
class Player(
    @Id val id: UUID = UUID.randomUUID(),
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "wallets")
class Wallet(
    @Id val userId: UUID,
    @Column(precision = 19, scale = 2) var cash: BigDecimal = ZERO,
    @Column(precision = 19, scale = 2) var bonus: BigDecimal = ZERO,
    @Version var version: Long = 0
)

@Entity
@Table(name = "deposits")
class Deposit(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    @Column(precision = 19, scale = 2) val amount: BigDecimal,
    var status: String = "PENDING",
    val createdAt: Instant = Instant.now(),
    var completedAt: Instant? = null
)

@Entity
@Table(name = "deposit_callbacks")
class DepositCallback(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val transactionId: UUID,
    val depositId: UUID? = null,
    @Column(precision = 19, scale = 2) val amount: BigDecimal,
    val status: String,
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "bonuses")
class Bonus(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val depositId: UUID,
    @Column(precision = 19, scale = 2) val grantedAmount: BigDecimal,
    @Column(precision = 19, scale = 2) var wageredAmount: BigDecimal = ZERO,
    @Column(precision = 19, scale = 2) val targetAmount: BigDecimal = grantedAmount * BigDecimal(20),
    var status: String = "ACTIVE",
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = createdAt.plusSeconds(7 * 24 * 60 * 60),
    var closedAt: Instant? = null
)

@Entity
@Table(name = "rounds")
class Round(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    @Column(precision = 19, scale = 2) val stake: BigDecimal,
    @Column(precision = 19, scale = 2) val stakeCash: BigDecimal,
    @Column(precision = 19, scale = 2) val stakeBonus: BigDecimal,
    val payoutPercent: Int,
    @Column(precision = 19, scale = 2) val payout: BigDecimal,
    @Column(precision = 19, scale = 2) val payoutCash: BigDecimal,
    @Column(precision = 19, scale = 2) val payoutBonus: BigDecimal,
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "ledger")
class LedgerEntry(
    @Id val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val type: String,
    @Column(precision = 19, scale = 2) val cashDelta: BigDecimal,
    @Column(precision = 19, scale = 2) val bonusDelta: BigDecimal,
    @Column(precision = 19, scale = 2) val cashAfter: BigDecimal,
    @Column(precision = 19, scale = 2) val bonusAfter: BigDecimal,
    val depositId: UUID? = null,
    val roundId: UUID? = null,
    val bonusId: UUID? = null,
    val createdAt: Instant = Instant.now()
)
