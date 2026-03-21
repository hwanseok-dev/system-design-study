package io.lucky.security.application

data class OrderCancelPayload(
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    val side: String,
)
