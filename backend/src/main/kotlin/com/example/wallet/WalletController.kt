package com.example.wallet

import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class MoneyRequest(
    val userId: UUID,
    @field:Pattern(regexp = "(?:0|[1-9][0-9]{0,15})\\.[0-9]{2}") val amount: String
) {
    fun value(): BigDecimal {
        val value = amount.toBigDecimal()

        if (value <= ZERO) throw ApiException(400, "VALIDATION_ERROR", "Amount must be positive")

        return value
    }
}

data class DepositCallbackRequest(
    val userId: UUID,
    val transactionId: UUID,
    @field:Pattern(regexp = "(?:0|[1-9][0-9]{0,15})\\.[0-9]{2}") val amount: String
) {
    fun value(): BigDecimal {
        val value = amount.toBigDecimal()
        if (value <= ZERO) throw ApiException(400, "VALIDATION_ERROR", "Amount must be positive")
        return value
    }
}

@RestController
class WalletController(private val service: WalletService) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(): Any = service.register()

    @GetMapping("/cashier/wallet")
    fun wallet(@RequestParam userId: UUID): Any = service.wallet(userId)

    @PostMapping("/cashier/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDeposit(@Valid @RequestBody request: MoneyRequest, @RequestHeader("Idempotency-Key") key: UUID): Any =
        service.createDeposit(request.userId, request.value(), key)

    @GetMapping("/cashier/deposits")
    fun deposits(
        @RequestParam userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) status: String?
    ): Any = service.deposits(userId, page, size, status)

    @GetMapping("/cashier/deposits/{depositId}")
    fun deposit(@RequestParam userId: UUID, @PathVariable depositId: UUID): Any = service.deposit(userId, depositId)

    @PostMapping("/cashier/deposit-callbacks")
    fun callback(@Valid @RequestBody request: DepositCallbackRequest): ResponseEntity<Any> {
        val result = service.processDepositCallback(request.userId, request.transactionId, request.value())
        return ResponseEntity.status(result.status).body(result.body)
    }

    @GetMapping("/cashier/deposit-callbacks")
    fun callbacks(
        @RequestParam userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): Any = service.depositCallbacks(userId, page, size)

    @GetMapping("/cashier/ledger")
    fun ledger(
        @RequestParam userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): Any = service.ledger(userId, page, size)

    @PostMapping("/bet")
    fun bet(@Valid @RequestBody request: MoneyRequest, @RequestHeader("Idempotency-Key") key: UUID): Any {
        Thread.sleep(1000)
        return service.bet(request.userId, request.value(), key)
    }

    @GetMapping("/health")
    fun health() = mapOf("status" to "UP")
}
