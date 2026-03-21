package io.lucky.security.application

import java.math.BigDecimal

data class OrderExecutePayload(
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    val side: String,
    val orderType: String,
    val quantity: Int,
    val price: BigDecimal?,
)
