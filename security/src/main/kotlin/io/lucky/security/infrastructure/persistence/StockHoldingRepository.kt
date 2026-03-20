package io.lucky.security.infrastructure.persistence

import io.lucky.security.domain.balance.StockHolding
import org.springframework.data.jpa.repository.JpaRepository

interface StockHoldingRepository : JpaRepository<StockHolding, Long> {
    fun findByUserIdAndStockCode(
        userId: Long,
        stockCode: String,
    ): StockHolding?
}
