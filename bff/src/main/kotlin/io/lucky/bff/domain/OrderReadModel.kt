package io.lucky.bff.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "order_read_model")
class OrderReadModel(
    @Id
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    var stockName: String? = null,
    val side: String,
    val orderType: String,
    var status: String,
    val quantity: Int,
    var filledQuantity: Int = 0,
    val price: BigDecimal? = null,
    var avgFilledPrice: BigDecimal? = null,
    var lockedAmount: BigDecimal = BigDecimal.ZERO,
    var eventVersion: Long = 0,
    val createdAt: Instant,
    var updatedAt: Instant = Instant.now(),
)
