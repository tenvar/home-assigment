package com.example.wallet

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

@Repository
class IdempotencyStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    fun replay(userId: UUID, operation: String, key: UUID, amount: BigDecimal): JsonNode? {
        val entries = jdbc.query(
            "SELECT request_hash, response_body::text FROM idempotency_keys WHERE user_id = ? AND operation = ? AND key = ?",
            { row, _ -> row.getString(1) to row.getString(2) }, userId, operation, key
        )
        val entry = entries.firstOrNull() ?: return null
        if (entry.first != hash(amount)) {
            throw ApiException(409, "IDEMPOTENCY_CONFLICT", "This key was already used for another request")
        }
        return mapper.readTree(entry.second)
    }

    fun save(userId: UUID, operation: String, key: UUID, amount: BigDecimal, status: Int, response: Any) {
        jdbc.update(
            "INSERT INTO idempotency_keys (user_id, operation, key, request_hash, response_status, response_body, created_at) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP)",
            userId, operation, key, hash(amount), status, mapper.writeValueAsString(response)
        )
    }

    private fun hash(amount: BigDecimal): String = MessageDigest.getInstance("SHA-256")
        .digest(amount.money().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
