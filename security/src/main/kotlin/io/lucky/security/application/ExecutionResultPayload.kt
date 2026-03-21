package io.lucky.security.application

import java.math.BigDecimal
import java.time.Instant

data class ExecutionResultPayload(
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    val side: String,
    val quantity: Int,
    val price: BigDecimal,
    val exchangeExecId: String,
    val executedAt: Instant,
)
