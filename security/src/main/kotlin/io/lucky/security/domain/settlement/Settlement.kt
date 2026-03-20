package io.lucky.security.domain.settlement

import io.lucky.security.domain.order.OrderSide
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "settlement")
class Settlement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "execution_id", nullable = false, unique = true)
    val executionId: Long,
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "stock_code", nullable = false)
    val stockCode: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val side: OrderSide,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SettlementStatus = SettlementStatus.PENDING,
    @Column(nullable = false)
    val quantity: Int,
    @Column(nullable = false)
    val amount: BigDecimal,
    @Column(name = "settlement_date", nullable = false)
    val settlementDate: LocalDate,
    @Column(name = "processed_at")
    var processedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun process() {
        status = status.transitTo(SettlementStatus.PROCESSING)
    }

    fun complete() {
        status = status.transitTo(SettlementStatus.COMPLETED)
        processedAt = Instant.now()
    }

    fun fail() {
        status = status.transitTo(SettlementStatus.FAILED)
    }

    fun retry() {
        status = status.transitTo(SettlementStatus.PROCESSING)
    }
}
