package com.example.wallet

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TransactionHistoryStore(private val jdbc: JdbcTemplate) {
    fun find(userId: UUID, page: Int, size: Int): Any {
        val total = jdbc.queryForObject(
            "SELECT (SELECT COUNT(*) FROM deposits WHERE user_id = ?) + (SELECT COUNT(*) FROM rounds WHERE user_id = ?)",
            Long::class.java, userId, userId
        ) ?: 0
        val items = jdbc.query(
            """
            SELECT id, transaction_type, amount, status, created_at, completed_at
            FROM (
                SELECT id, 'DEPOSIT' AS transaction_type, amount, status, created_at, completed_at
                FROM deposits WHERE user_id = ?
                UNION ALL
                SELECT id, 'GAME' AS transaction_type, stake AS amount,
                    CASE
                        WHEN payout = 0 THEN 'LOST'
                        WHEN payout < stake THEN 'PARTIAL_RETURN'
                        WHEN payout = stake THEN 'STAKE_RETURNED'
                        ELSE 'WON'
                    END AS status,
                    created_at, created_at AS completed_at
                FROM rounds WHERE user_id = ?
            ) transactions
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { row, _ ->
                mapOf(
                    "id" to row.getObject("id", UUID::class.java),
                    "type" to row.getString("transaction_type"),
                    "amount" to row.getBigDecimal("amount").money(),
                    "currency" to "EUR",
                    "status" to row.getString("status"),
                    "createdAt" to row.getTimestamp("created_at").toInstant(),
                    "completedAt" to row.getTimestamp("completed_at")?.toInstant()
                )
            },
            userId, userId, size, page.toLong() * size
        )
        return mapOf(
            "items" to items,
            "page" to page,
            "size" to size,
            "totalElements" to total,
            "totalPages" to if (total == 0L) 0 else ((total + size - 1) / size)
        )
    }
}
