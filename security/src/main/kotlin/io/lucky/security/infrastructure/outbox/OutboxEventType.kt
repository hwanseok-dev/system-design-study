package io.lucky.security.infrastructure.outbox

enum class OutboxEventType {
    ORDER_VALIDATE,
    ORDER_EXECUTE,
    ORDER_FILLED,
    EXECUTION_SETTLED,
}
