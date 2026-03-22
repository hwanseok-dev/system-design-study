package io.lucky.bff.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "execution_read_model")
class ExecutionReadModel(
    @Id
    val executionId: Long,
    val orderId: Long,
    val userId: Long,
    val stockCode: String,
    var stockName: String? = null,
    val side: String,
    val quantity: Int,
    val price: BigDecimal,
    val amount: BigDecimal,
    val executedAt: Instant,
)
