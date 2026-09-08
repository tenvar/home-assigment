package com.example.wallet

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface PlayerRepository : JpaRepository<Player, UUID>

interface WalletRepository : JpaRepository<Wallet, UUID> {
    @Query("select w.version from Wallet w where w.userId = :userId")
    fun findVersionByUserId(userId: UUID): Long?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    fun lockByUserId(userId: UUID): Wallet?
}

interface DepositRepository : JpaRepository<Deposit, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): Deposit?
    fun findByUserId(userId: UUID, pageable: Pageable): Page<Deposit>
    fun findByUserIdAndStatus(userId: UUID, status: String, pageable: Pageable): Page<Deposit>
}

interface DepositCallbackRepository : JpaRepository<DepositCallback, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<DepositCallback>
}

interface BonusRepository : JpaRepository<Bonus, UUID> {
    fun findByUserId(userId: UUID): Bonus?
    fun findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(status: String, before: Instant): List<Bonus>
}

interface RoundRepository : JpaRepository<Round, UUID>

interface LedgerRepository : JpaRepository<LedgerEntry, UUID> {
    fun findByUserId(userId: UUID, pageable: Pageable): Page<LedgerEntry>
}
