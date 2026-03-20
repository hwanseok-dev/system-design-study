package io.lucky.security.api

import io.lucky.security.domain.order.OrderSide
import io.lucky.security.domain.order.OrderType
import java.math.BigDecimal

data class CreateOrderRequest(
    val userId: Long,
    val stockCode: String,
    val orderType: OrderType,
    val side: OrderSide,
    val quantity: Int,
    val price: BigDecimal?,
)

data class OrderResponse(
    val id: Long,
    val userId: Long,
    val stockCode: String,
    val orderType: String,
    val side: String,
    val status: String,
    val quantity: Int,
    val filledQuantity: Int,
    val price: BigDecimal?,
    val avgFilledPrice: BigDecimal?,
    val lockedAmount: BigDecimal,
)
