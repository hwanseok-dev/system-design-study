package io.lucky.orchestrator.application

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lucky.orchestrator.api.WorkflowEdgeRequest
import io.lucky.orchestrator.api.WorkflowNodeRequest
import io.lucky.orchestrator.domain.EntityNotFoundException
import io.lucky.orchestrator.domain.LogAction
import io.lucky.orchestrator.domain.response.TaskResponse
import io.lucky.orchestrator.domain.workflow.Workflow
import io.lucky.orchestrator.domain.workflow.WorkflowNode
import io.lucky.orchestrator.infrastructure.messaging.TaskResponseMessage
import io.lucky.orchestrator.infrastructure.persistence.TaskRepository
import io.lucky.orchestrator.infrastructure.persistence.TaskResponseRepository
import io.lucky.orchestrator.infrastructure.persistence.WorkflowRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class WorkflowService(
    private val workflowRepository: WorkflowRepository,
    private val taskRepository: TaskRepository,
    private val taskResponseRepository: TaskResponseRepository,
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

    fun handleSuccessResponse(msg: TaskResponseMessage) {
        taskResponseRepository.save(
            TaskResponse(
                workflowId = msg.workflowId,
                taskId = msg.taskId,
                sequence = msg.sequence,
                payload = msg.payload,
            ),
        )

        val countKey = "{wf:${msg.workflowId}}:task:${msg.taskId}:count"
        val count = redisTemplate.opsForValue().increment(countKey)!!
        if (count == 1L) {
            redisTemplate.expire(countKey, Duration.ofHours(24))
        }

        val expectedCount = getExpectedCount(msg.workflowId, msg.taskId)
        if (count >= expectedCount.toLong()) {
            taskExecutionService.completeTask(msg.workflowId, msg.taskId)
        }
    }

    @Transactional
    fun handleFailureResponse(msg: TaskResponseMessage) {
        taskResponseRepository.save(
            TaskResponse(
                workflowId = msg.workflowId,
                taskId = msg.taskId,
                sequence = msg.sequence,
                payload = msg.payload,
                status = "FAILED",
            ),
        )

        val workflow = getWorkflow(msg.workflowId)
        workflow.failTask(msg.taskId)
        logger.info {
            "action=${LogAction.FAIL_TASK} workflowId=${msg.workflowId} taskId=${msg.taskId}"
        }
    }

    private fun getExpectedCount(
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
