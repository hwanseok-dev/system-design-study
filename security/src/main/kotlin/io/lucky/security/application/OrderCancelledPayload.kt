package io.lucky.security.application

import java.math.BigDecimal

data class OrderCancelledPayload(
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    val side: String,
    val cancelledQuantity: Int,
    val restoredAmount: BigDecimal?,
)
