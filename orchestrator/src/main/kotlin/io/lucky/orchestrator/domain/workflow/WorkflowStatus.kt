package io.lucky.orchestrator.domain.workflow

enum class WorkflowStatus {
    CREATED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    private val transitions: Set<WorkflowStatus>
        get() =
            when (this) {
                CREATED -> setOf(RUNNING)
                RUNNING -> setOf(SUCCEEDED, FAILED)
                SUCCEEDED -> emptySet()
                FAILED -> emptySet()
            }

    fun canTransitTo(target: WorkflowStatus): Boolean = target in transitions

    fun transitTo(target: WorkflowStatus): WorkflowStatus {
        check(canTransitTo(target)) { "Cannot transit from $this to $target" }
        return target
    }
}
