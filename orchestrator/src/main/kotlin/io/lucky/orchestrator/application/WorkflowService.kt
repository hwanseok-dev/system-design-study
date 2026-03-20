package io.lucky.orchestrator.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.api.WorkflowEdgeRequest
import io.lucky.orchestrator.api.WorkflowNodeRequest
import io.lucky.orchestrator.domain.EntityNotFoundException
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.workflow.Workflow
import io.lucky.orchestrator.domain.workflow.WorkflowNode
import io.lucky.orchestrator.infrastructure.persistence.TaskRepository
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import jakarta.persistence.OptimisticLockException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val taskRepository: TaskRepository,
    private val taskExecutionService: TaskExecutionService,
    private val redisTemplate: StringRedisTemplate,
) {
    @Transactional
    fun create(
        name: String,
        nodes: List<WorkflowNodeRequest>,
        edges: List<WorkflowEdgeRequest>,
    ): Workflow {
        val workflow = Workflow(name = name)

        val taskIds = nodes.map { it.taskId }
        val tasks = taskRepository.findAllById(taskIds).associateBy { it.id }

        nodes.forEach { node ->
            val task = tasks[node.taskId] ?: throw EntityNotFoundException("Task", node.taskId)
            workflow.addTask(task, task.expectedCount)
        }

        edges.forEach { edge ->
            val parent = tasks[edge.parentTaskId] ?: throw EntityNotFoundException("Task", edge.parentTaskId)
            val child = tasks[edge.childTaskId] ?: throw EntityNotFoundException("Task", edge.childTaskId)
            workflow.addEdge(parent, child)
        }

        val saved = workflowRepository.save(workflow)
        logger.info { "action=${LogAction.CREATE_WORKFLOW} workflowId=${saved.id} name=$name taskCount=${nodes.size}" }
        return saved
    }

    @Transactional
    fun start(workflowId: Long): List<WorkflowNode> {
        val workflow = getWorkflow(workflowId)
        val readyNodes = workflow.start()

        taskExecutionService.saveTaskExecutionRequests(workflowId, readyNodes)

        logger.info {
            "action=${LogAction.START_WORKFLOW} workflowId=$workflowId readyNodes=${readyNodes.map { it.task.name }}"
        }
        return readyNodes
    }

    @Transactional(readOnly = true)
    fun findById(workflowId: Long): Workflow = getWorkflow(workflowId)

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
        val failKey = "{wf:$workflowId}:task:$taskId:failed"
        if (redisTemplate.hasKey(failKey)) {
            logger.info {
                "action=${LogAction.SKIP_SUCCESS_ALREADY_FAILED} workflowId=$workflowId taskId=$taskId"
            }
            return
        }

        val workflow = getWorkflow(workflowId)
        val nextNodes = workflow.completeTask(taskId)
        taskExecutionService.saveTaskExecutionRequests(workflowId, nextNodes)
        logger.info {
            "action=${LogAction.COMPLETE_TASK} workflowId=$workflowId taskId=$taskId nextTasks=${nextNodes.size}"
        }
    }

    @Retryable(
        value = [OptimisticLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100),
    )
    @Transactional
    fun failTask(
        workflowId: Long,
        taskId: Long,
    ) {
        val workflow = getWorkflow(workflowId)
        workflow.failTask(taskId)
        logger.info {
            "action=${LogAction.FAIL_TASK} workflowId=$workflowId taskId=$taskId"
        }
    }

    fun getExpectedCount(
        workflowId: Long,
        taskId: Long,
    ): Int {
        val workflow = getWorkflow(workflowId)
        return workflow.findNode(taskId).expectedCount
    }

    private fun getWorkflow(workflowId: Long): Workflow =
        workflowRepository
            .findByIdWithDetails(workflowId)
            .orElseThrow { EntityNotFoundException("Workflow", workflowId) }
}
