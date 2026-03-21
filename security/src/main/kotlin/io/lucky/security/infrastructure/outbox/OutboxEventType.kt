package io.lucky.security.infrastructure.outbox

enum class OutboxEventType {
    ORDER_VALIDATE,
    ORDER_EXECUTE,
    ORDER_CANCEL,
    ORDER_FILLED,
    ORDER_CANCELLED,
    EXECUTION_SETTLED,
}
