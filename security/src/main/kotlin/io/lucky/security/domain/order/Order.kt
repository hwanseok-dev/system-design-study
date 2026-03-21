package io.lucky.security.domain.order

import io.lucky.security.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal

@Entity
@Table(name = "stock_order")
class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "stock_code", nullable = false)
    val stockCode: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    val orderType: OrderType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: OrderSide,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,
    @Column(nullable = false)
    val quantity: Int,
    @Column(name = "filled_quantity", nullable = false)
    var filledQuantity: Int = 0,
    val price: BigDecimal? = null,
    @Column(name = "avg_filled_price")
    var avgFilledPrice: BigDecimal? = null,
    @Column(name = "locked_amount", nullable = false)
    var lockedAmount: BigDecimal = BigDecimal.ZERO,
    @Version
    val version: Long = 0,
) : BaseEntity() {
    fun initLockedAmount() {
        check(lockedAmount == BigDecimal.ZERO) { "lockedAmount already initialized: $lockedAmount" }
        lockedAmount =
            when (orderType) {
                OrderType.LIMIT -> price!! * quantity.toBigDecimal()
                OrderType.MARKET -> throw UnsupportedOperationException("Market order lock TBD")
            }
    }

    // Execution price can differ from order price (e.g. limit 50000, filled at 49000).
    // lockedAmount must track actual remaining lock so cancel restores the correct amount.
    fun consumeLockedAmount(amount: BigDecimal) {
        check(lockedAmount >= amount) { "Cannot consume more than locked: locked=$lockedAmount, consume=$amount" }
        lockedAmount -= amount
    }

    fun validate() {
        status = status.transitTo(OrderStatus.VALIDATED)
    }

    fun submit() {
        status = status.transitTo(OrderStatus.SUBMITTED)
    }

    fun cancel(): Int {
        status = status.transitTo(OrderStatus.CANCELLED)
        return quantity - filledQuantity
    }

    fun reject() {
        status = status.transitTo(OrderStatus.REJECTED)
    }

    fun settle() {
        status = status.transitTo(OrderStatus.SETTLED)
    }

    fun remainingQuantity(): Int = quantity - filledQuantity

    /**
     * Apply a partial or full execution to this order.
     * Recalculates the weighted average filled price and transitions to
     * PARTIAL_FILLED or FILLED depending on whether filledQuantity reaches quantity.
     */
    fun applyExecution(
        executedQuantity: Int,
        executedPrice: BigDecimal,
    ): OrderStatus {
        check(status == OrderStatus.SUBMITTED || status == OrderStatus.PARTIAL_FILLED) {
            "Cannot apply execution in status $status"
        }
        val previousTotal = (avgFilledPrice ?: BigDecimal.ZERO) * filledQuantity.toBigDecimal()
        val executionTotal = executedPrice * executedQuantity.toBigDecimal()
        filledQuantity += executedQuantity
        avgFilledPrice = (previousTotal + executionTotal) / filledQuantity.toBigDecimal()
        status = status.transitTo(if (filledQuantity >= quantity) OrderStatus.FILLED else OrderStatus.PARTIAL_FILLED)
        return status
    }
}
