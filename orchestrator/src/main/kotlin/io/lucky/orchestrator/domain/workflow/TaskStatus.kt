package io.lucky.orchestrator.domain.workflow

enum class TaskStatus {
    CREATED,
    WAITING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    private val transitions: Set<TaskStatus>
        get() =
            when (this) {
                CREATED -> setOf(WAITING)
                WAITING -> setOf(RUNNING, FAILED)
                RUNNING -> setOf(SUCCEEDED, FAILED)
                SUCCEEDED -> emptySet()
                FAILED -> emptySet()
            }

    fun canTransitTo(target: TaskStatus): Boolean = target in transitions

    fun transitTo(target: TaskStatus): TaskStatus {
        check(canTransitTo(target)) { "Cannot transit from $this to $target" }
        return target
    }
}
