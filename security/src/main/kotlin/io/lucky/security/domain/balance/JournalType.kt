package io.lucky.security.domain.balance

enum class JournalType {
    ORDER_LOCK,
    ORDER_UNLOCK,
    STOCK_LOCK,
    STOCK_UNLOCK,
    BUY_EXECUTION,
    SELL_EXECUTION,
    SETTLEMENT,
}
