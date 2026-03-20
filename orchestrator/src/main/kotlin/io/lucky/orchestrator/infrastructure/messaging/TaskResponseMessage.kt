package io.lucky.orchestrator.infrastructure.messaging

data class TaskResponseMessage(
    val workflowId: Long,
    val taskId: Long,
    val sequence: Int,
    val payload: String? = null,
)
