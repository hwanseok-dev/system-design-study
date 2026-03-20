package io.lucky.orchestrator.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.lucky.orchestrator.domain.workflow.WorkflowNode
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutePayload
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequest
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequestRepository
import org.springframework.stereotype.Service

@Service
class TaskExecutionService(
    private val taskExecutionRequestRepository: TaskExecutionRequestRepository,
    private val objectMapper: ObjectMapper,
) {
    fun saveTaskExecutionRequests(
        workflowId: Long,
        nodes: List<WorkflowNode>,
    ) {
        nodes.forEach { node ->
            val payload =
                TaskExecutePayload(
                    workflowId = workflowId,
                    taskId = node.task.id,
                    taskName = node.task.name,
                    queueName = node.task.queueName,
                    expectedCount = node.expectedCount,
                )
            taskExecutionRequestRepository.save(
                TaskExecutionRequest(
                    workflowId = workflowId,
                    taskId = node.task.id,
                    payload = objectMapper.writeValueAsString(payload),
                ),
            )
        }
    }
}
