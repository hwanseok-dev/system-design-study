package io.lucky.bff.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant

data class PortfolioSnapshotId(
    val userId: Long = 0,
    val stockCode: String = "",
) : Serializable

@Entity
@Table(name = "portfolio_snapshot")
@IdClass(PortfolioSnapshotId::class)
class PortfolioSnapshot(
    @Id val userId: Long,
    @Id val stockCode: String,
    var stockName: String? = null,
    var quantity: Int = 0,
    var lockedQuantity: Int = 0,
    var avgBuyPrice: BigDecimal = BigDecimal.ZERO,
    var cashAmount: BigDecimal? = null,
    var lockedCash: BigDecimal? = null,
    var updatedAt: Instant = Instant.now(),
)
