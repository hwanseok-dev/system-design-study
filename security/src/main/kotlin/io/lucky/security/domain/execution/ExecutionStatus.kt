package io.lucky.security.domain.execution

enum class ExecutionStatus {
    RECEIVED,
    APPLIED,
    SETTLED,
    FAILED,
    ;

    private val transitions: Set<ExecutionStatus>
        get() =
            when (this) {
                RECEIVED -> setOf(APPLIED)
                APPLIED -> setOf(SETTLED, FAILED)
                SETTLED -> emptySet()
                FAILED -> emptySet()
            }

    fun canTransitTo(target: ExecutionStatus): Boolean = target in transitions

    fun transitTo(target: ExecutionStatus): ExecutionStatus {
        check(canTransitTo(target)) { "Cannot transit from $this to $target" }
        return target
    }
}
