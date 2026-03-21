package io.lucky.security.application

import io.lucky.security.domain.EntityNotFoundException
import io.lucky.security.domain.balance.BalanceJournal
import io.lucky.security.domain.balance.JournalType
import io.lucky.security.infrastructure.persistence.BalanceJournalRepository
import io.lucky.security.infrastructure.persistence.BalanceRepository
import io.lucky.security.infrastructure.persistence.StockHoldingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class BalanceService(
    private val balanceRepository: BalanceRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val journalRepository: BalanceJournalRepository,
) {
    fun lockCache(
        userId: Long,
        orderId: Long,
        stockCode: String,
        amount: BigDecimal,
    ) {
        val balance =
            balanceRepository.findByUserId(userId)
                ?: throw EntityNotFoundException("Balance", userId)
        balance.lockCash(amount)
        journalRepository.save(
            BalanceJournal(
                userId = userId,
                journalType = JournalType.ORDER_LOCK,
                referenceType = "ORDER",
                referenceId = orderId,
                stockCode = stockCode,
                cashDelta = amount.negate(),
            ),
        )
    }

    fun unlockCash(
        userId: Long,
        orderId: Long,
        stockCode: String,
        amount: BigDecimal,
    ) {
        val balance =
            balanceRepository.findByUserId(userId)
                ?: throw EntityNotFoundException("Balance", userId)
        balance.unlockCash(amount)
        journalRepository.save(
            BalanceJournal(
                userId = userId,
                journalType = JournalType.ORDER_UNLOCK,
                referenceType = "ORDER",
                referenceId = orderId,
                stockCode = stockCode,
                cashDelta = amount,
            ),
        )
    }

    fun lockStock(
        userId: Long,
        orderId: Long,
        stockCode: String,
        quantity: Int,
    ) {
        val holding =
            stockHoldingRepository.findByUserIdAndStockCode(userId, stockCode)
                ?: throw EntityNotFoundException("StockHolding", "$userId:$stockCode")
        holding.lockQuantity(quantity)
        journalRepository.save(
            BalanceJournal(
                userId = userId,
                journalType = JournalType.STOCK_LOCK,
                referenceType = "ORDER",
                referenceId = orderId,
                stockCode = stockCode,
                quantityDelta = -quantity,
            ),
        )
    }

    fun unlockStock(
        userId: Long,
        orderId: Long,
        stockCode: String,
        quantity: Int,
    ) {
        val holding =
            stockHoldingRepository.findByUserIdAndStockCode(userId, stockCode)
                ?: throw EntityNotFoundException("StockHolding", "$userId:$stockCode")
        holding.unlockQuantity(quantity)
        journalRepository.save(
            BalanceJournal(
                userId = userId,
                journalType = JournalType.STOCK_UNLOCK,
                referenceType = "ORDER",
                referenceId = orderId,
                stockCode = stockCode,
                quantityDelta = quantity,
            ),
        )
    }
}
