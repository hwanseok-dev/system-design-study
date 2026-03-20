package io.lucky.orchestrator.infrastructure.outbox

data class TaskExecutePayload(
    val workflowId: Long,
    val taskId: Long,
    val taskName: String,
    val queueName: String,
    val expectedCount: Int,
)
