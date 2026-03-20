package io.lucky.security.domain.balance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "stock_holding",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "stock_code"])],
)
class StockHolding(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "stock_code", nullable = false)
    val stockCode: String,
    @Column(nullable = false)
    var quantity: Int = 0,
    @Column(name = "locked_quantity", nullable = false)
    var lockedQuantity: Int = 0,
    @Column(name = "avg_buy_price", nullable = false)
    var avgBuyPrice: BigDecimal = BigDecimal.ZERO,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    val version: Long = 0,
) {
    fun availableQuantity(): Int = quantity - lockedQuantity

    fun lockQuantity(qty: Int) {
        check(availableQuantity() >= qty) { "Insufficient stock: available=${availableQuantity()}, required=$qty" }
        lockedQuantity += qty
    }

    fun unlockQuantity(qty: Int) {
        check(lockedQuantity >= qty) { "Invalid unlock: locked=$lockedQuantity, unlock=$qty" }
        lockedQuantity -= qty
    }

    fun confirmSellExecution(qty: Int) {
        check(lockedQuantity >= qty && quantity >= qty) { "Invalid sell confirm" }
        quantity -= qty
        lockedQuantity -= qty
    }

    /**
     * Add purchased stock and recalculate the weighted average buy price.
     */
    fun addQuantity(
        qty: Int,
        buyPrice: BigDecimal,
    ) {
        val totalCost = avgBuyPrice * quantity.toBigDecimal() + buyPrice * qty.toBigDecimal()
        quantity += qty
        avgBuyPrice = if (quantity > 0) totalCost / quantity.toBigDecimal() else BigDecimal.ZERO
    }
}
