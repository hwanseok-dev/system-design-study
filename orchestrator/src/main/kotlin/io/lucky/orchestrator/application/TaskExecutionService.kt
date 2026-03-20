package io.lucky.orchestrator.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.domain.EntityNotFoundException
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.workflow.WorkflowNode
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutePayload
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequest
import io.lucky.orchestrator.infrastructure.outbox.TaskExecutionRequestRepository
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import jakarta.persistence.OptimisticLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class TaskExecutionService(
    private val workflowRepository: WorkflowRepository,
    private val taskExecutionRequestRepository: TaskExecutionRequestRepository,
    private val objectMapper: ObjectMapper,
) {
    @Retryable(
        value = [OptimisticLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100),
    )
    @Transactional
    fun completeTask(
        workflowId: Long,
        taskId: Long,
    ) {
        val workflow =
            workflowRepository
                .findByIdWithDetails(workflowId)
                .orElseThrow { EntityNotFoundException("Workflow", workflowId) }
        val nextNodes = workflow.completeTask(taskId)
        saveTaskExecutionRequests(workflowId, nextNodes)
        logger.info {
            "action=${LogAction.COMPLETE_TASK} workflowId=$workflowId taskId=$taskId nextTasks=${nextNodes.size}"
        }
    }

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
