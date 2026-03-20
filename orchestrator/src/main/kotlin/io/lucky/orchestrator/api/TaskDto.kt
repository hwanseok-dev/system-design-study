package io.lucky.orchestrator.api

data class CreateTaskRequest(
    val name: String,
    val queueName: String,
    val expectedCount: Int = 1,
)

data class TaskResponse(
    val id: Long,
    val name: String,
    val queueName: String,
    val expectedCount: Int,
)
