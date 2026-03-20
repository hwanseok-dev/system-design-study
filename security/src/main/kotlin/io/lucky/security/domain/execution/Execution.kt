package io.lucky.security.domain.execution

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
@Table(name = "execution")
class Execution(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
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
    var status: ExecutionStatus = ExecutionStatus.RECEIVED,
    @Column(nullable = false)
    val quantity: Int,
    @Column(nullable = false)
    val price: BigDecimal,
    @Column(nullable = false)
    val amount: BigDecimal,
    @Column(name = "exchange_exec_id", nullable = false, unique = true)
    val exchangeExecId: String,
    @Column(name = "executed_at", nullable = false)
    val executedAt: Instant,
    @Column(name = "settlement_date", nullable = false)
    val settlementDate: LocalDate,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    fun apply() {
        status = status.transitTo(ExecutionStatus.APPLIED)
    }

    fun settle() {
        status = status.transitTo(ExecutionStatus.SETTLED)
    }

    fun fail() {
        status = status.transitTo(ExecutionStatus.FAILED)
    }
}
