package io.lucky.security.application

import java.math.BigDecimal

data class OrderFilledPayload(
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    val side: String,
    val filledQuantity: Int,
    val avgFilledPrice: BigDecimal,
)
