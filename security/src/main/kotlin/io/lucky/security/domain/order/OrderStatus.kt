package io.lucky.security.domain.order

enum class OrderStatus {
    PENDING,
    VALIDATED,
    SUBMITTED,
    PARTIAL_FILLED,
    FILLED,
    SETTLED,
    REJECTED,
    CANCELLED,
    ;

    private val transitions: Set<OrderStatus>
        get() =
            when (this) {
                PENDING -> setOf(VALIDATED, REJECTED)
                VALIDATED -> setOf(SUBMITTED, REJECTED)
                SUBMITTED -> setOf(PARTIAL_FILLED, FILLED, CANCELLED)
                PARTIAL_FILLED -> setOf(PARTIAL_FILLED, FILLED, CANCELLED)
                FILLED -> setOf(SETTLED)
                SETTLED -> emptySet()
                REJECTED -> emptySet()
                CANCELLED -> emptySet()
            }

    fun canTransitTo(target: OrderStatus): Boolean = target in transitions

    fun transitTo(target: OrderStatus): OrderStatus {
        check(canTransitTo(target)) { "Cannot transit from $this to $target" }
        return target
    }
}
