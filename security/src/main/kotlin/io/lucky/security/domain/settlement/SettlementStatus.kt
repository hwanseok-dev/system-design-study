package io.lucky.security.domain.settlement

enum class SettlementStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    ;

    private val transitions: Set<SettlementStatus>
        get() =
            when (this) {
                PENDING -> setOf(PROCESSING)
                PROCESSING -> setOf(COMPLETED, FAILED)
                FAILED -> setOf(PROCESSING)
                COMPLETED -> emptySet()
            }

    fun canTransitTo(target: SettlementStatus): Boolean = target in transitions

    fun transitTo(target: SettlementStatus): SettlementStatus {
        check(canTransitTo(target)) { "Cannot transit from $this to $target" }
        return target
    }
}
