package com.example.wallet

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PaymentScheduler(
    private val bonuses: BonusRepository,
    private val service: WalletService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun expireBonuses() {
        bonuses.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc("ACTIVE", Instant.now())
            .forEach {
                try {
                    service.expireBonus(it.userId)
                } catch (error: Exception) {
                    logger.error("Could not expire bonus {}", it.id, error)
                }
            }
    }
}
